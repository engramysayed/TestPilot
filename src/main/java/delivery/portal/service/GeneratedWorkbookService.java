package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.codegen.ProvenStep;
import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.heal.HealWorkbookPatcher;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.KeelPath;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GeneratedWorkbookService {
    private static final String META_FILE = "latest-meta.json";
    private static final String EXCEL_FILE = "latest.xlsx";
    private static final String CSV_FILE = "latest.csv";

    private final DeliveryPortalProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeneratedWorkbookService(DeliveryPortalProperties props) {
        this.props = props;
    }

    public Path generatedDir(String projectId) {
        return Path.of(props.getStoreRoot(), projectId, "generated");
    }

    public void saveFromCases(String projectId, List<ManualTestCase> cases, String source, String sourceRef)
            throws Exception {
        saveFromCases(projectId, cases, source, sourceRef, null);
    }

    public void saveFromCases(
            String projectId,
            List<ManualTestCase> cases,
            String source,
            String sourceRef,
            String model
    ) throws Exception {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("No test cases to save");
        }
        Path dir = generatedDir(projectId);
        Files.createDirectories(dir);
        Path excelPath = dir.resolve(EXCEL_FILE);
        ManualTcExcelWriter.write(excelPath, cases);
        Files.writeString(dir.resolve(CSV_FILE), GeneratedTcCsvParser.toCsv(cases), StandardCharsets.UTF_8);

        KeelPathCounts keelPathCounts = KeelPathCounts.from(cases);

        Map<String, Object> previous = readMeta(dir);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("projectId", projectId);
        meta.put("tcCount", cases.size());
        meta.put("keelPathCounts", keelPathCounts.toMap());
        meta.put("source", source == null ? "GENERATE" : source);
        meta.put("sourceRef", sourceRef == null ? "" : sourceRef);
        if (model != null && !model.isBlank()) {
            meta.put("model", model);
        } else if (previous.get("model") != null && !String.valueOf(previous.get("model")).isBlank()) {
            meta.put("model", previous.get("model"));
        }
        Object priorNotes = previous.get("coverageNotes");
        if (priorNotes != null) {
            meta.put("coverageNotes", String.valueOf(priorNotes));
        }
        Object priorAuto = previous.get("automationNotesByTc");
        if (priorAuto != null) {
            meta.put("automationNotesByTc", priorAuto);
        }
        meta.put("createdAt", Instant.now().toString());
        meta.put("excelFile", EXCEL_FILE);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_FILE).toFile(), meta);
    }

    /** Persists coverage notes into workbook meta (does not rewrite Excel rows). */
    public Map<String, Object> updateCoverageNotes(String projectId, String coverageNotes) throws Exception {
        Path dir = generatedDir(projectId);
        requireExcel(projectId);
        Map<String, Object> meta = new LinkedHashMap<>(readMeta(dir));
        meta.put("projectId", projectId);
        meta.put("coverageNotes", coverageNotes == null ? "" : coverageNotes);
        meta.put("updatedAt", Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_FILE).toFile(), meta);
        Map<String, Object> out = new LinkedHashMap<>(
                describe(projectId).orElseThrow(() -> new IllegalStateException("NO_GENERATED_WORKBOOK")));
        out.put("coverageNotes", coverageNotes == null ? "" : coverageNotes);
        return out;
    }

    /** Merge heal automation notes for a TC into workbook meta for Automate reviewers. */
    @SuppressWarnings("unchecked")
    public void mergeAutomationNotes(String projectId, String tcId, List<String> notes) throws Exception {
        if (projectId == null || projectId.isBlank() || tcId == null || tcId.isBlank()
                || notes == null || notes.isEmpty()) {
            return;
        }
        Path dir = generatedDir(projectId);
        if (!Files.isRegularFile(dir.resolve(EXCEL_FILE))) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>(readMeta(dir));
        Object raw = meta.get("automationNotesByTc");
        Map<String, Object> byTc = raw instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        List<String> existing = new ArrayList<>();
        Object prior = byTc.get(tcId);
        if (prior instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    existing.add(String.valueOf(o));
                }
            }
        }
        for (String note : notes) {
            if (note != null && !note.isBlank() && !existing.contains(note)) {
                existing.add(note);
            }
        }
        byTc.put(tcId, existing);
        meta.put("automationNotesByTc", byTc);
        meta.put("updatedAt", Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_FILE).toFile(), meta);
    }

    public record HealPatchApplyResult(
            boolean excelSaved,
            boolean skipped,
            String reason,
            List<String> appliedSummaries
    ) {
        public HealPatchApplyResult {
            reason = reason == null ? "" : reason;
            appliedSummaries = appliedSummaries == null ? List.of() : List.copyOf(appliedSummaries);
        }
    }

    /**
     * After successful heal recovery: deterministically patch leave-empty Steps/TestData on the
     * saved generated workbook, merge automation notes, and append unmatched notes to coverage.
     * Best-effort for callers — gate failure skips Excel save but still persists notes when possible.
     */
    public HealPatchApplyResult applyHealRecoveryPatch(
            String projectId,
            String tcId,
            List<ProvenStep> recoverySteps,
            List<String> automationNotes,
            String baseUrl
    ) throws Exception {
        if (projectId == null || projectId.isBlank() || tcId == null || tcId.isBlank()) {
            return new HealPatchApplyResult(false, true, "missing projectId/tcId", List.of());
        }
        Path dir = generatedDir(projectId);
        if (!Files.isRegularFile(dir.resolve(EXCEL_FILE))) {
            return new HealPatchApplyResult(false, true, "NO_GENERATED_WORKBOOK", List.of());
        }
        String normalizedTcId = tcId.trim();
        List<ManualTestCase> cases = new ExcelTcReader().read(requireExcel(projectId));
        int index = -1;
        for (int i = 0; i < cases.size(); i++) {
            if (normalizedTcId.equals(cases.get(i).tcId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
            appendHealCoverageNotes(projectId, normalizedTcId, automationNotes);
            return new HealPatchApplyResult(false, true, "unknown tcId", List.of());
        }

        HealWorkbookPatcher.PatchResult patched = HealWorkbookPatcher.patch(
                cases.get(index), recoverySteps, automationNotes);

        if (patched.cellsChanged()) {
            List<ManualTestCase> next = new ArrayList<>(cases);
            next.set(index, patched.patchedCase());
            List<ManualTestCase> repaired = TcImportRepair.repairCases(next);
            List<String> gateErrors = GenerateQualityGate.validate(repaired, baseUrl);
            if (!gateErrors.isEmpty()) {
                mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
                appendHealCoverageNotes(projectId, normalizedTcId, patched.unmatchedNotes());
                return new HealPatchApplyResult(
                        false,
                        true,
                        "HEAL_WORKBOOK_PATCH_SKIPPED: " + String.join("; ", gateErrors),
                        patched.appliedSummaries());
            }
            Map<String, Object> meta = readMeta(dir);
            String source = String.valueOf(meta.getOrDefault("source", "GENERATE"));
            String sourceRef = String.valueOf(meta.getOrDefault("sourceRef", ""));
            Object modelObj = meta.get("model");
            String model = modelObj == null ? null : String.valueOf(modelObj);
            if (model != null && model.isBlank()) {
                model = null;
            }
            saveFromCases(projectId, repaired, source, sourceRef, model);
            mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
            appendHealCoverageNotes(projectId, normalizedTcId, patched.unmatchedNotes());
            return new HealPatchApplyResult(true, false, "", patched.appliedSummaries());
        }

        mergeAutomationNotes(projectId, normalizedTcId, automationNotes);
        appendHealCoverageNotes(projectId, normalizedTcId, patched.unmatchedNotes());
        return new HealPatchApplyResult(false, false, "no cell changes", patched.appliedSummaries());
    }

    /** Append unmatched heal notes under a Heal notes heading; dedupe exact line text. */
    void appendHealCoverageNotes(String projectId, String tcId, List<String> notes) throws Exception {
        if (projectId == null || projectId.isBlank() || notes == null || notes.isEmpty()) {
            return;
        }
        Path dir = generatedDir(projectId);
        if (!Files.isRegularFile(dir.resolve(EXCEL_FILE))) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>(readMeta(dir));
        String prior = meta.get("coverageNotes") == null ? "" : String.valueOf(meta.get("coverageNotes"));
        StringBuilder block = new StringBuilder();
        block.append("### Heal notes (").append(tcId == null ? "" : tcId.trim()).append(")\n");
        boolean any = false;
        for (String n : notes) {
            if (n == null || n.isBlank()) {
                continue;
            }
            String line = n.trim();
            if (prior.contains(line)) {
                continue;
            }
            block.append("- ").append(line).append('\n');
            any = true;
        }
        if (!any) {
            return;
        }
        String updated = prior.isBlank()
                ? block.toString().trim()
                : prior.trim() + "\n\n" + block.toString().trim();
        meta.put("coverageNotes", updated);
        meta.put("updatedAt", Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_FILE).toFile(), meta);
    }

    public void saveFromCsvFile(String projectId, Path csvPath, String source, String sourceRef) throws Exception {
        saveFromCsvFile(projectId, csvPath, source, sourceRef, null);
    }

    public void saveFromCsvFile(
            String projectId,
            Path csvPath,
            String source,
            String sourceRef,
            String model
    ) throws Exception {
        String raw = Files.readString(csvPath, StandardCharsets.UTF_8);
        saveFromCases(projectId, GeneratedTcCsvParser.parse(raw), source, sourceRef, model);
    }

    public Optional<Map<String, Object>> describe(String projectId) throws Exception {
        Path dir = generatedDir(projectId);
        Path excel = dir.resolve(EXCEL_FILE);
        if (!Files.isRegularFile(excel)) {
            return Optional.empty();
        }
        Map<String, Object> meta = readMeta(dir);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", true);
        out.put("projectId", projectId);
        out.put("tcCount", meta.getOrDefault("tcCount", 0));
        out.put("keelPathCounts", meta.getOrDefault("keelPathCounts", Map.of()));
        out.put("source", meta.getOrDefault("source", "GENERATE"));
        out.put("sourceRef", meta.getOrDefault("sourceRef", ""));
        out.put("model", meta.getOrDefault("model", ""));
        out.put("createdAt", meta.getOrDefault("createdAt", ""));
        out.put("excelFileName", EXCEL_FILE);
        out.put("coverageNotes", meta.getOrDefault("coverageNotes", ""));
        out.put("automationNotesByTc", meta.getOrDefault("automationNotesByTc", Map.of()));
        return Optional.of(out);
    }

    public Path requireExcel(String projectId) {
        Path excel = generatedDir(projectId).resolve(EXCEL_FILE);
        if (!Files.isRegularFile(excel)) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK");
        }
        return excel;
    }

    /**
     * Patches KeelPath on stored workbook rows by {@code tcId}, then re-saves excel/csv/meta.
     * Returns the same shape as {@link #describe(String)} with refreshed {@code keelPathCounts}.
     */
    public Map<String, Object> updateKeelPaths(String projectId, Map<String, String> updates) throws Exception {
        if (updates == null || updates.isEmpty()) {
            Path emptyDir = generatedDir(projectId);
            Map<String, Object> out = new LinkedHashMap<>(
                    describe(projectId).orElseThrow(() -> new IllegalStateException("NO_GENERATED_WORKBOOK")));
            Path csv = emptyDir.resolve(CSV_FILE);
            if (Files.isRegularFile(csv)) {
                out.put("csv", Files.readString(csv, StandardCharsets.UTF_8));
            }
            return out;
        }
        Path dir = generatedDir(projectId);
        Path excel = requireExcel(projectId);
        Map<String, Object> meta = readMeta(dir);

        List<ManualTestCase> cases = new ExcelTcReader().read(excel);
        if (cases.isEmpty()) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String tcId = entry.getKey().trim();
            String rawPath = entry.getValue();
            if (rawPath == null || rawPath.isBlank()) {
                normalized.put(tcId, "");
            } else {
                normalized.put(tcId, KeelPath.parse(rawPath).name());
            }
        }

        List<ManualTestCase> patched = new ArrayList<>(cases.size());
        for (ManualTestCase tc : cases) {
            String newPath = normalized.get(tc.tcId());
            if (newPath != null) {
                patched.add(new ManualTestCase(
                        tc.tcId(),
                        tc.title(),
                        tc.preconditions(),
                        tc.steps(),
                        tc.expectedResult(),
                        tc.priority(),
                        tc.tags(),
                        tc.visualAssertion(),
                        tc.testData(),
                        newPath
                ));
            } else {
                patched.add(tc);
            }
        }

        String source = String.valueOf(meta.getOrDefault("source", "GENERATE"));
        String sourceRef = String.valueOf(meta.getOrDefault("sourceRef", ""));
        Object modelObj = meta.get("model");
        String model = modelObj == null ? null : String.valueOf(modelObj);
        if (model != null && model.isBlank()) {
            model = null;
        }

        saveFromCases(projectId, patched, source, sourceRef, model);
        Map<String, Object> out = new LinkedHashMap<>(
                describe(projectId).orElseThrow(() -> new IllegalStateException("NO_GENERATED_WORKBOOK")));
        out.put("csv", GeneratedTcCsvParser.toCsv(patched));
        return out;
    }

    /**
     * Replaces editable fields on one stored workbook row by {@code tcId}, runs repair + quality gate,
     * then re-saves excel/csv/meta. Returns describe-shaped payload with refreshed {@code csv} and {@code row}.
     */
    public Map<String, Object> updateCaseFields(
            String projectId,
            String tcId,
            Map<String, String> fields,
            String baseUrl
    ) throws Exception {
        if (tcId == null || tcId.isBlank()) {
            throw new IllegalArgumentException("tcId is required");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("At least one field is required");
        }
        Path dir = generatedDir(projectId);
        Path excel = requireExcel(projectId);
        Map<String, Object> meta = readMeta(dir);

        List<ManualTestCase> cases = new ExcelTcReader().read(excel);
        if (cases.isEmpty()) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK");
        }

        String normalizedTcId = tcId.trim();
        boolean found = false;
        List<ManualTestCase> patched = new ArrayList<>(cases.size());
        ManualTestCase updatedRow = null;
        for (ManualTestCase tc : cases) {
            if (!normalizedTcId.equals(tc.tcId())) {
                patched.add(tc);
                continue;
            }
            found = true;
            updatedRow = mergeCaseFields(tc, fields);
            patched.add(updatedRow);
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown tcId: " + normalizedTcId);
        }

        List<ManualTestCase> repaired = TcImportRepair.repairCases(patched);
        List<String> gateErrors = GenerateQualityGate.validate(repaired, baseUrl);
        if (!gateErrors.isEmpty()) {
            throw GenerateQualityGate.failureException(gateErrors);
        }

        String source = String.valueOf(meta.getOrDefault("source", "GENERATE"));
        String sourceRef = String.valueOf(meta.getOrDefault("sourceRef", ""));
        Object modelObj = meta.get("model");
        String model = modelObj == null ? null : String.valueOf(modelObj);
        if (model != null && model.isBlank()) {
            model = null;
        }

        saveFromCases(projectId, repaired, source, sourceRef, model);

        ManualTestCase savedRow = repaired.stream()
                .filter(tc -> normalizedTcId.equals(tc.tcId()))
                .findFirst()
                .orElse(updatedRow);

        Map<String, Object> out = new LinkedHashMap<>(
                describe(projectId).orElseThrow(() -> new IllegalStateException("NO_GENERATED_WORKBOOK")));
        out.put("csv", GeneratedTcCsvParser.toCsv(repaired));
        out.put("row", toRowMap(savedRow));
        return out;
    }

    private static ManualTestCase mergeCaseFields(ManualTestCase tc, Map<String, String> fields) {
        String title = fieldOrExisting(fields, "title", tc.title());
        String preconditions = fieldOrExisting(fields, "preconditions", tc.preconditions());
        String steps = fieldOrExisting(fields, "steps", tc.steps());
        String expectedResult = fieldOrExisting(fields, "expectedResult", tc.expectedResult());
        String priority = fieldOrExisting(fields, "priority", tc.priority());
        String tags = fieldOrExisting(fields, "tags", tc.tags());
        String visualAssertion = fieldOrExisting(fields, "visualAssertion", tc.visualAssertion());
        String testData = fieldOrExisting(fields, "testData", tc.testData());
        String keelPath = tc.keelPath();
        if (fields.containsKey("keelPath")) {
            String rawPath = fields.get("keelPath");
            if (rawPath == null || rawPath.isBlank()) {
                keelPath = "";
            } else {
                keelPath = KeelPath.parse(rawPath).name();
            }
        }
        return new ManualTestCase(
                tc.tcId(),
                title,
                preconditions,
                steps,
                expectedResult,
                priority,
                tags,
                visualAssertion,
                testData,
                keelPath
        );
    }

    private static String fieldOrExisting(Map<String, String> fields, String key, String existing) {
        if (!fields.containsKey(key)) {
            return existing;
        }
        String value = fields.get(key);
        return value == null ? "" : value;
    }

    static Map<String, Object> toRowMap(ManualTestCase tc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tcId", tc.tcId());
        row.put("title", tc.title());
        row.put("preconditions", tc.preconditions());
        row.put("steps", tc.steps());
        row.put("expectedResult", tc.expectedResult());
        row.put("priority", tc.priority());
        row.put("tags", tc.tags());
        row.put("visualAssertion", tc.visualAssertion());
        row.put("testData", tc.testData());
        row.put("keelPath", tc.keelPath());
        return row;
    }

    /** Copy stored workbook to a job-scoped temp path so jobs do not mutate the saved copy. */
    public Path copyForJob(String projectId) throws Exception {
        return materializeForJob(projectId, null, null);
    }

    public List<ManualTestCase> readCases(String projectId) throws Exception {
        return new ExcelTcReader().read(requireExcel(projectId));
    }

    public Map<String, Object> listCases(String projectId) throws Exception {
        List<ManualTestCase> cases = readCases(projectId);
        Map<String, Object> out = new LinkedHashMap<>(
                describe(projectId).orElseThrow(() -> new IllegalStateException("NO_GENERATED_WORKBOOK")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ManualTestCase tc : cases) {
            rows.add(toRowMap(tc));
        }
        out.put("cases", rows);
        return out;
    }

    /**
     * Deletes workbook rows by TC_ID. Refuses to delete the last remaining case.
     */
    public Map<String, Object> deleteCases(String projectId, List<String> tcIds) throws Exception {
        if (tcIds == null || tcIds.isEmpty()) {
            throw new IllegalArgumentException("tcIds is required");
        }
        java.util.LinkedHashSet<String> want = new java.util.LinkedHashSet<>();
        for (String id : tcIds) {
            if (id != null && !id.isBlank()) {
                want.add(id.trim());
            }
        }
        if (want.isEmpty()) {
            throw new IllegalArgumentException("tcIds is required");
        }
        Path dir = generatedDir(projectId);
        Map<String, Object> meta = readMeta(dir);
        List<ManualTestCase> cases = readCases(projectId);
        List<ManualTestCase> kept = new ArrayList<>();
        int removed = 0;
        for (ManualTestCase tc : cases) {
            if (want.contains(tc.tcId())) {
                removed++;
            } else {
                kept.add(tc);
            }
        }
        if (removed == 0) {
            throw new IllegalArgumentException("No matching tcIds in workbook");
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete the last test case in the workbook");
        }
        String source = String.valueOf(meta.getOrDefault("source", "GENERATE"));
        String sourceRef = String.valueOf(meta.getOrDefault("sourceRef", ""));
        Object modelObj = meta.get("model");
        String model = modelObj == null ? null : String.valueOf(modelObj);
        if (model != null && model.isBlank()) {
            model = null;
        }
        saveFromCases(projectId, kept, source, sourceRef, model);
        Map<String, Object> out = listCases(projectId);
        out.put("removedCount", removed);
        return out;
    }

    /**
     * Build a job-temp xlsx from library selection and/or upload cases.
     * {@code selectedTcIds} null/empty with library = all library rows.
     * Upload rows win on duplicate {@code TC_ID}.
     */
    public Path materializeForJob(
            String projectId,
            List<String> selectedTcIds,
            List<ManualTestCase> uploadCases
    ) throws Exception {
        List<ManualTestCase> merged = WorkbookJobMaterializer.merge(
                hasWorkbook(projectId) ? readCases(projectId) : List.of(),
                selectedTcIds,
                uploadCases == null ? List.of() : uploadCases
        );
        if (merged.isEmpty()) {
            throw new IllegalStateException("NO_CASES_FOR_JOB");
        }
        Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId, "generated");
        Files.createDirectories(uploadDir);
        Path dest = uploadDir.resolve(UUID.randomUUID() + ".xlsx");
        ManualTcExcelWriter.write(dest, merged);
        return dest;
    }

    public boolean hasWorkbook(String projectId) {
        return Files.isRegularFile(generatedDir(projectId).resolve(EXCEL_FILE));
    }

    private Map<String, Object> readMeta(Path dir) throws Exception {
        Path metaPath = dir.resolve(META_FILE);
        if (!Files.isRegularFile(metaPath)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = objectMapper.readValue(metaPath.toFile(), Map.class);
        return meta == null ? Map.of() : meta;
    }
}
