package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
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

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("projectId", projectId);
        meta.put("tcCount", cases.size());
        meta.put("keelPathCounts", keelPathCounts.toMap());
        meta.put("source", source == null ? "GENERATE" : source);
        meta.put("sourceRef", sourceRef == null ? "" : sourceRef);
        if (model != null && !model.isBlank()) {
            meta.put("model", model);
        }
        meta.put("createdAt", Instant.now().toString());
        meta.put("excelFile", EXCEL_FILE);
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
        Path source = requireExcel(projectId);
        Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId, "generated");
        Files.createDirectories(uploadDir);
        Path dest = uploadDir.resolve(UUID.randomUUID() + ".xlsx");
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
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
