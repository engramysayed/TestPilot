package delivery.portal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ManualTestCase;
import delivery.hunt.HuntBugDedupe;
import delivery.portal.model.JobRecord;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persists hunt-bug triage overlays on the hunt-run artifact directory so UI refresh
 * keeps FIRST_PASS|FLAKY|CONFIRMED|DISMISSED + notes. CONFIRMED never writes the library;
 * {@link #promote} is the only library write path.
 */
@Service
public class HuntTriageService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> STATUSES = Set.of("FIRST_PASS", "FLAKY", "CONFIRMED", "DISMISSED");
    private static final Set<String> ADMISSIONS = Set.of(
            "ORACLE", "NETWORK", "MAP_BOUND_ASSERT", "TOAST_PROMOTED", "REJECTED");

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;

    public HuntTriageService(PortalStore store, GeneratedWorkbookService workbooks) {
        this.store = store;
        this.workbooks = workbooks;
    }

    public Path huntRoot(String projectId, String jobId) {
        return store.projectDiskRoot(projectId).resolve("hunt-runs").resolve(jobId);
    }

    public void requireHuntJob(String projectId, String jobId, Long uid) {
        JobRecord job = store.getOwnedJob(jobId, uid).orElse(null);
        if (job == null || !projectId.equals(job.getProjectId()) || job.getJobKind() != JobRecord.JobKind.HUNT) {
            // Still allow reading pack artifacts written for the project even if job row is gone,
            // as long as the hunt-run directory exists under the project.
            Path root = huntRoot(projectId, jobId);
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("unknown hunt job");
            }
        }
    }

    public List<Map<String, Object>> listBugs(String projectId, String jobId) throws Exception {
        Map<String, Map<String, Object>> overlay = readOverlay(projectId, jobId);
        List<Map<String, Object>> rows = readBugReport(projectId, jobId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(toApiBug(row, overlay.get(bugIdOf(row))));
        }
        return out;
    }

    public Map<String, Object> summary(String projectId, String jobId) throws Exception {
        List<Map<String, Object>> bugs = listBugs(projectId, jobId);
        int firstPass = 0, flaky = 0, confirmed = 0, dismissed = 0;
        for (Map<String, Object> b : bugs) {
            switch (String.valueOf(b.getOrDefault("status", "FIRST_PASS"))) {
                case "FLAKY" -> flaky++;
                case "CONFIRMED" -> confirmed++;
                case "DISMISSED" -> dismissed++;
                default -> firstPass++;
            }
        }
        Path root = huntRoot(projectId, jobId);
        int coverageUrls = readCoverageUrlCount(root);
        boolean plateau = readPlateau(root);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("firstPass", firstPass);
        out.put("flaky", flaky);
        out.put("confirmed", confirmed);
        out.put("dismissed", dismissed);
        out.put("coverageUrls", coverageUrls);
        out.put("plateau", plateau);
        return out;
    }

    public Map<String, Object> patchStatus(String projectId, String jobId, String bugId,
                                           String status, String note) throws Exception {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("status must be FIRST_PASS, FLAKY, CONFIRMED, or DISMISSED");
        }
        Map<String, Object> bug = findBug(projectId, jobId, bugId);
        if (bug == null) {
            return null;
        }
        Map<String, Map<String, Object>> overlay = readOverlay(projectId, jobId);
        Map<String, Object> entry = new LinkedHashMap<>(overlay.getOrDefault(bugId, Map.of()));
        entry.put("status", normalized);
        if (note != null) {
            entry.put("note", note);
        }
        // AE lock: CONFIRMED ≠ promote — never touch library here.
        overlay.put(bugId, entry);
        writeOverlay(projectId, jobId, overlay);
        return toApiBug(bug, entry);
    }

    /**
     * Explicit library promotion. Requires prior CONFIRMED. Creates/links a TC draft row.
     */
    public Map<String, Object> promote(String projectId, String jobId, String bugId, String actorEmail)
            throws Exception {
        Map<String, Object> bug = findBug(projectId, jobId, bugId);
        if (bug == null) {
            return null;
        }
        Map<String, Map<String, Object>> overlay = readOverlay(projectId, jobId);
        Map<String, Object> entry = new LinkedHashMap<>(overlay.getOrDefault(bugId, Map.of()));
        String status = String.valueOf(entry.getOrDefault("status",
                bug.getOrDefault("status", defaultStatus(bug))));
        if (!"CONFIRMED".equals(status)) {
            throw new IllegalStateException("NOT_CONFIRMED");
        }
        if (Boolean.TRUE.equals(entry.get("promoted")) && entry.get("tcDraftId") != null) {
            Map<String, Object> already = new LinkedHashMap<>();
            already.put("status", "PROMOTED");
            already.put("bugId", bugId);
            already.put("jobId", jobId);
            already.put("tcDraftId", entry.get("tcDraftId"));
            already.put("message", "Already promoted — CONFIRMED alone never writes the library.");
            return already;
        }
        // Excel/TcIdentity: TC_<ALNUM_UNDERSCORE> only (uppercase).
        String tcDraftId = "TC_DRAFT_" + sanitizeId(bugId).toUpperCase(Locale.ROOT);
        ManualTestCase draft = toDraftCase(tcDraftId, bug);
        List<ManualTestCase> library = workbooks.hasWorkbook(projectId)
                ? new ArrayList<>(workbooks.readCases(projectId))
                : new ArrayList<>();
        library.removeIf(c -> tcDraftId.equals(c.tcId()));
        library.add(draft);
        workbooks.saveFromCases(projectId, library, "HUNT_TRIAGE_PROMOTE",
                actorEmail == null ? "hunt-triage" : actorEmail);
        entry.put("promoted", true);
        entry.put("tcDraftId", tcDraftId);
        entry.put("status", "CONFIRMED");
        overlay.put(bugId, entry);
        writeOverlay(projectId, jobId, overlay);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "PROMOTED");
        out.put("bugId", bugId);
        out.put("jobId", jobId);
        out.put("tcDraftId", tcDraftId);
        out.put("message", "TC draft created/linked. CONFIRMED alone never writes the library.");
        return out;
    }

    public List<Map<String, Object>> listProjectBugs(String projectId, String statusFilter) throws Exception {
        Path runs = store.projectDiskRoot(projectId).resolve("hunt-runs");
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(runs)) {
            return out;
        }
        String want = statusFilter == null || statusFilter.isBlank()
                ? null
                : statusFilter.trim().toUpperCase(Locale.ROOT);
        try (var stream = Files.list(runs)) {
            for (Path jobDir : stream.filter(Files::isDirectory).sorted().toList()) {
                String jobId = jobDir.getFileName().toString();
                for (Map<String, Object> bug : listBugs(projectId, jobId)) {
                    if (want != null && !want.equals(String.valueOf(bug.get("status")))) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>(bug);
                    row.put("jobId", jobId);
                    out.add(row);
                }
            }
        }
        return out;
    }

    private Map<String, Object> findBug(String projectId, String jobId, String bugId) throws Exception {
        for (Map<String, Object> row : readBugReport(projectId, jobId)) {
            if (bugIdOf(row).equals(bugId)) {
                return row;
            }
        }
        return null;
    }

    private List<Map<String, Object>> readBugReport(String projectId, String jobId) throws Exception {
        Path report = huntRoot(projectId, jobId).resolve("bug-report.json");
        if (!Files.isRegularFile(report)) {
            return List.of();
        }
        String raw = Files.readString(report).trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        if (raw.startsWith("{")) {
            Map<String, Object> wrap = MAPPER.readValue(raw, new TypeReference<>() {});
            // HuntPackWriter writes `rows: [...]`; older runs may still carry `bugs: [...]`.
            Object bugs = wrap.get("rows");
            if (!(bugs instanceof List<?>)) {
                bugs = wrap.get("bugs");
            }
            if (bugs instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cast = (Map<String, Object>) m;
                        out.add(new LinkedHashMap<>(cast));
                    }
                }
                return out;
            }
        }
        return MAPPER.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
    }

    private Map<String, Map<String, Object>> readOverlay(String projectId, String jobId) throws Exception {
        Path path = huntRoot(projectId, jobId).resolve("triage-state.json");
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> root = MAPPER.readValue(Files.readString(path), new TypeReference<>() {});
        Object bugs = root.get("bugs");
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (bugs instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> vm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) vm;
                    out.put(String.valueOf(e.getKey()), new LinkedHashMap<>(cast));
                }
            }
        }
        return out;
    }

    private void writeOverlay(String projectId, String jobId, Map<String, Map<String, Object>> overlay)
            throws Exception {
        Path root = huntRoot(projectId, jobId);
        Files.createDirectories(root);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("bugs", overlay);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(root.resolve("triage-state.json").toFile(), doc);
    }

    private Map<String, Object> toApiBug(Map<String, Object> row, Map<String, Object> overlay) {
        Map<String, Object> b = new LinkedHashMap<>();
        String bugId = bugIdOf(row);
        b.put("bugId", bugId);
        String fp = str(row.get("fingerprint"));
        if (fp.isBlank()) {
            fp = HuntBugDedupe.fingerprint(row);
        }
        b.put("fingerprint", fp);
        b.put("title", str(row.get("title")));
        b.put("severity", str(row.get("severity")).isBlank() ? "major" : str(row.get("severity")));
        b.put("admission", normalizeAdmission(row));
        String status = overlay != null && overlay.get("status") != null
                ? String.valueOf(overlay.get("status"))
                : defaultStatus(row);
        b.put("status", status);
        b.put("toastObservedAfterSettle", truthy(row.get("toastObservedAfterSettle")));
        b.put("reproduceSteps", reproduceSteps(row));
        b.put("url", str(row.get("url")));
        b.put("blockedLocators", listOfStrings(row.get("blockedLocators")));
        b.put("evidenceRefs", evidenceRefs(row));
        boolean promoted = overlay != null && Boolean.TRUE.equals(overlay.get("promoted"));
        b.put("promoted", promoted);
        if (overlay != null && overlay.get("note") != null) {
            b.put("note", String.valueOf(overlay.get("note")));
        }
        if (overlay != null && overlay.get("tcDraftId") != null) {
            b.put("tcDraftId", String.valueOf(overlay.get("tcDraftId")));
        }
        return b;
    }

    static String bugIdOf(Map<String, Object> row) {
        String id = str(row.get("bugId"));
        if (!id.isBlank()) {
            return id;
        }
        id = str(row.get("tcId"));
        if (!id.isBlank()) {
            return id;
        }
        String fp = str(row.get("fingerprint"));
        if (!fp.isBlank()) {
            return "bug_" + Integer.toHexString(fp.hashCode());
        }
        return "bug_" + Integer.toHexString(str(row.get("title")).hashCode());
    }

    private static String defaultStatus(Map<String, Object> row) {
        String s = str(row.get("status"));
        if (STATUSES.contains(s.toUpperCase(Locale.ROOT))) {
            return s.toUpperCase(Locale.ROOT);
        }
        String qa = str(row.get("qaStatus")).toUpperCase(Locale.ROOT);
        if ("FLAKY".equals(qa)) {
            return "FLAKY";
        }
        Object dup = row.get("dupCount");
        if (dup instanceof Number n && n.intValue() >= 2) {
            return "FLAKY";
        }
        return "FIRST_PASS";
    }

    private static String normalizeAdmission(Map<String, Object> row) {
        String a = str(row.get("admission")).toUpperCase(Locale.ROOT);
        if (ADMISSIONS.contains(a)) {
            return a;
        }
        String ev = str(row.get("admissionEvidence")).toUpperCase(Locale.ROOT);
        if (ADMISSIONS.contains(ev)) {
            return ev;
        }
        if (ev.contains("NETWORK")) {
            return "NETWORK";
        }
        if (ev.contains("TOAST") || ev.contains("COPY")) {
            return "TOAST_PROMOTED";
        }
        if (ev.contains("MAP") || ev.contains("ASSERT")) {
            return "MAP_BOUND_ASSERT";
        }
        if (ev.contains("REJECT")) {
            return "REJECTED";
        }
        return a.isBlank() ? "ORACLE" : a;
    }

    private static List<Map<String, Object>> reproduceSteps(Map<String, Object> row) {
        Object steps = row.get("reproduceSteps");
        List<Map<String, Object>> out = new ArrayList<>();
        if (steps instanceof List<?> list) {
            for (Object o : list) {
                if (out.size() >= 5) {
                    break;
                }
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("action", str(m.get("action")).isBlank() ? "step" : str(m.get("action")));
                    step.put("target", str(m.get("target")));
                    if (m.get("value") != null && !str(m.get("value")).isBlank()) {
                        step.put("value", str(m.get("value")));
                    }
                    out.add(step);
                } else if (o != null) {
                    String line = String.valueOf(o).trim();
                    if (!line.isEmpty()) {
                        out.add(Map.of("action", "step", "target", line));
                    }
                }
            }
        } else {
            String repro = str(row.get("reproScript"));
            if (repro.isBlank()) {
                repro = str(row.get("repro"));
            }
            if (!repro.isBlank()) {
                for (String line : repro.split("\\R")) {
                    if (out.size() >= 5) {
                        break;
                    }
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        out.add(Map.of("action", "step", "target", t));
                    }
                }
            }
        }
        return out;
    }

    private static List<String> evidenceRefs(Map<String, Object> row) {
        Object refs = row.get("evidenceRefs");
        if (refs instanceof List<?>) {
            return listOfStrings(refs);
        }
        Object shots = row.get("screenshotUrls");
        if (shots instanceof List<?>) {
            return listOfStrings(shots);
        }
        return List.of();
    }

    private static List<String> listOfStrings(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                String s = String.valueOf(o);
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static ManualTestCase toDraftCase(String tcId, Map<String, Object> bug) {
        StringBuilder steps = new StringBuilder();
        int i = 1;
        for (Map<String, Object> step : reproduceSteps(bug)) {
            steps.append(i++).append(". ")
                    .append(str(step.get("action"))).append(' ')
                    .append(str(step.get("target")));
            if (step.get("value") != null) {
                steps.append(" = ").append(str(step.get("value")));
            }
            steps.append('\n');
        }
        if (steps.isEmpty()) {
            steps.append("1. Reproduce from hunt finding\n");
        }
        String expected = str(bug.get("expected"));
        if (expected.isBlank()) {
            expected = "Finding no longer reproduces";
        }
        String sev = str(bug.get("severity"));
        String priority = sev.toLowerCase(Locale.ROOT).contains("critical") ? "P0"
                : sev.toLowerCase(Locale.ROOT).contains("minor") ? "P2" : "P1";
        return new ManualTestCase(
                tcId,
                str(bug.get("title")).isBlank() ? tcId : str(bug.get("title")),
                "Promoted from hunt triage (draft)",
                steps.toString().trim(),
                expected,
                priority,
                "hunt-triage,draft",
                "",
                "",
                "AUTOMATE",
                ""
        );
    }

    private static int readCoverageUrlCount(Path root) {
        Path cov = root.resolve("coverage-map.json");
        try {
            if (!Files.isRegularFile(cov)) {
                return 0;
            }
            Map<String, Object> m = MAPPER.readValue(Files.readString(cov), new TypeReference<>() {});
            Object urls = m.get("urls");
            if (urls instanceof List<?> list) {
                return list.size();
            }
            urls = m.get("visitedUrls");
            if (urls instanceof List<?> list) {
                return list.size();
            }
            Object n = m.get("visitedUrlCount");
            if (n instanceof Number num) {
                return num.intValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static boolean readPlateau(Path root) {
        Path summary = root.resolve("SUMMARY.md");
        try {
            if (!Files.isRegularFile(summary)) {
                return false;
            }
            String text = Files.readString(summary).toUpperCase(Locale.ROOT);
            return text.contains("NO_PROGRESS") || text.contains("INVENT_PLATEAU") || text.contains("PLATEAU");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(o));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String sanitizeId(String id) {
        String s = id == null ? "bug" : id.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return s.length() > 48 ? s.substring(0, 48) : s;
    }
}
