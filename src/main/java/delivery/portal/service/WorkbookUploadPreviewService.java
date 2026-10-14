package delivery.portal.service;

import delivery.excel.GenerateQualityGate;
import delivery.excel.ManualTestCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * B15: dry-run preview of a test-case upload plus a commit by preview token
 * (ux-patches/BACKEND_NEEDS.md §2). The preview saves nothing. Commit re-runs the normal upload merge,
 * so the quality gate still applies. Tokens are random, single-use, bound to project + user, expire after
 * {@code keel.workbook.preview-ttl-minutes} (default 10) and are rejected if the library changed since
 * the preview. In-memory only: a portal restart drops pending previews (the UI then re-uploads).
 */
@Service
public class WorkbookUploadPreviewService {

    public record Pending(String projectId, Long ownerId, String filename, byte[] bytes, String headRevision,
                          Instant expiresAt, int addedCount, int updatedCount, int unchangedCount) {
    }

    public enum CommitError { NOT_FOUND, EXPIRED, STALE }

    public static final class CommitException extends Exception {
        private final CommitError error;

        CommitException(CommitError error, String message) {
            super(message);
            this.error = error;
        }

        public CommitError error() {
            return error;
        }
    }

    static final List<String> FIELDS = List.of("Title", "Preconditions", "Steps", "ExpectedResult", "Priority",
            "Tags", "VisualAssertion", "TestData", "KeelPath", "CallBefore");
    private static final Map<String, Function<ManualTestCase, String>> GETTERS = new LinkedHashMap<>();

    static {
        GETTERS.put("Title", ManualTestCase::title);
        GETTERS.put("Preconditions", ManualTestCase::preconditions);
        GETTERS.put("Steps", ManualTestCase::steps);
        GETTERS.put("ExpectedResult", ManualTestCase::expectedResult);
        GETTERS.put("Priority", ManualTestCase::priority);
        GETTERS.put("Tags", ManualTestCase::tags);
        GETTERS.put("VisualAssertion", ManualTestCase::visualAssertion);
        GETTERS.put("TestData", ManualTestCase::testData);
        GETTERS.put("KeelPath", ManualTestCase::keelPath);
        GETTERS.put("CallBefore", ManualTestCase::callBefore);
    }

    private static final int MAX_PENDING = 200;
    private final GeneratedWorkbookService workbooks;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public WorkbookUploadPreviewService(GeneratedWorkbookService workbooks,
                                        @Value("${keel.workbook.preview-ttl-minutes:10}") long ttlMinutes) {
        this(workbooks, Duration.ofMinutes(ttlMinutes), Clock.systemUTC());
    }

    public WorkbookUploadPreviewService(GeneratedWorkbookService workbooks, Duration ttl, Clock clock) {
        this.workbooks = workbooks;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Map<String, Object> preview(String projectId, Long ownerId, String filename, byte[] bytes, String baseUrl)
            throws Exception {
        List<ManualTestCase> upload = workbooks.parseUploadCases(projectId, filename, bytes);
        List<ManualTestCase> existing = workbooks.hasWorkbook(projectId) ? workbooks.readCases(projectId) : List.of();
        Map<String, ManualTestCase> byId = new LinkedHashMap<>();
        for (ManualTestCase tc : existing) {
            if (tc != null && tc.tcId() != null && !tc.tcId().isBlank()) {
                byId.put(tc.tcId().trim(), tc);
            }
        }
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> updated = new ArrayList<>();
        List<Map<String, Object>> problems = new ArrayList<>();
        int unchanged = 0;
        for (int i = 0; i < upload.size(); i++) {
            ManualTestCase tc = upload.get(i);
            int row = i + 2; // row 1 is the header
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                problems.add(problem(null, row, "TC_ID", "This row has no TC_ID, so it will be skipped."));
                continue;
            }
            String id = tc.tcId().trim();
            for (String err : GenerateQualityGate.validate(List.of(tc), baseUrl)) {
                problems.add(problem(id, row, guessField(err), err));
            }
            ManualTestCase old = byId.get(id);
            if (old == null) {
                added.add(Map.of("tcId", id, "title", nz(tc.title())));
            } else {
                List<String> changed = changedFields(old, tc);
                if (changed.isEmpty()) {
                    unchanged++;
                } else {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("tcId", id);
                    u.put("title", nz(tc.title()));
                    u.put("changedFields", changed);
                    updated.add(u);
                }
            }
        }
        // whole-library rules (e.g. Call-before graph) that per-row checks can't see
        List<ManualTestCase> merged = GeneratedWorkbookService.mergeByTcId(existing, upload).cases();
        for (String err : GenerateQualityGate.validate(merged, baseUrl)) {
            boolean known = problems.stream().anyMatch(p -> err.equals(p.get("message")));
            if (!known) {
                problems.add(problem(null, null, guessField(err), err));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("updated", updated);
        out.put("unchanged", unchanged);
        out.put("problems", problems);
        out.put("previewToken", store(new Pending(projectId, ownerId, filename, bytes.clone(),
                workbooks.headRevisionId(projectId).orElse(null), clock.instant().plus(ttl),
                added.size(), updated.size(), unchanged)));
        out.put("expiresInSeconds", ttl.toSeconds());
        return out;
    }

    /** Validates and consumes the token; returns the pending upload to merge. */
    public Pending take(String token, String projectId, Long ownerId) throws Exception {
        Pending p = token == null ? null : pending.remove(token);
        if (p == null || !p.projectId().equals(projectId) || !Objects.equals(p.ownerId(), ownerId)) {
            if (p != null && !p.projectId().equals(projectId)) {
                pending.put(token, p); // don't let a wrong project burn someone else's token
            }
            throw new CommitException(CommitError.NOT_FOUND, "Unknown or already used preview token");
        }
        if (clock.instant().isAfter(p.expiresAt())) {
            throw new CommitException(CommitError.EXPIRED, "The preview expired; upload the file again");
        }
        String head = workbooks.headRevisionId(projectId).orElse(null);
        if (!Objects.equals(head, p.headRevision())) {
            throw new CommitException(CommitError.STALE, "The test cases changed after the preview; upload the file again");
        }
        return p;
    }

    private String store(Pending p) {
        Instant now = clock.instant();
        pending.values().removeIf(x -> now.isAfter(x.expiresAt()));
        if (pending.size() >= MAX_PENDING) {
            pending.entrySet().stream().min(Map.Entry.comparingByValue(
                    (a, b) -> a.expiresAt().compareTo(b.expiresAt()))).map(Map.Entry::getKey).ifPresent(pending::remove);
        }
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String token = "wbp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        pending.put(token, p);
        return token;
    }

    Optional<Pending> peek(String token) {
        return Optional.ofNullable(pending.get(token));
    }

    static List<String> changedFields(ManualTestCase a, ManualTestCase b) {
        List<String> out = new ArrayList<>();
        GETTERS.forEach((name, g) -> {
            if (!norm(g.apply(a)).equals(norm(g.apply(b)))) {
                out.add(name);
            }
        });
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static String guessField(String message) {
        if (message == null) {
            return null;
        }
        String m = message.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("tcid") || m.contains("tc_id")) return "TC_ID";
        if (m.contains("call-before") || m.contains("callbefore") || m.contains("call before")) return "CallBefore";
        if (m.contains("expected")) return "ExpectedResult";
        if (m.contains("step")) return "Steps";
        if (m.contains("keelpath") || m.contains("keel path")) return "KeelPath";
        if (m.contains("title")) return "Title";
        if (m.contains("test data") || m.contains("testdata")) return "TestData";
        return null;
    }

    private static Map<String, Object> problem(String tcId, Integer row, String field, String message) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tcId", tcId);
        p.put("row", row);
        p.put("field", field);
        p.put("message", message);
        return p;
    }
}
