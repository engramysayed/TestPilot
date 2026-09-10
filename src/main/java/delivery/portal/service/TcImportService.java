package delivery.portal.service;

import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.GeneratedTcJsonParser;
import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.portal.model.ProjectRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TcImportService {
    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;

    public TcImportService(PortalStore store, GeneratedWorkbookService workbooks) {
        this.store = store;
        this.workbooks = workbooks;
    }

    public Map<String, Object> importRaw(
            String projectId,
            Long ownerUserId,
            String raw,
            String formatHint,
            String source
    ) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Import text is required");
        }
        String stripped = TcImportRepair.stripMarkdownFences(raw);
        ParsedRaw parsed = parseRaw(stripped, formatHint);
        return importCases(projectId, ownerUserId, parsed.cases(), source, null, parsed.coverageNotes());
    }

    public Map<String, Object> importCases(
            String projectId,
            Long ownerUserId,
            List<ManualTestCase> cases,
            String source,
            String model
    ) throws Exception {
        return importCases(projectId, ownerUserId, cases, source, model, "");
    }

    public Map<String, Object> importCases(
            String projectId,
            Long ownerUserId,
            List<ManualTestCase> cases,
            String source,
            String model,
            String coverageNotes
    ) throws Exception {
        ProjectRecord project = store.getOwnedProject(projectId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project"));

        List<ManualTestCase> repaired = TcImportRepair.repairCases(cases);
        List<String> gateErrors = GenerateQualityGate.validate(repaired, project.getBaseUrl());
        if (!gateErrors.isEmpty()) {
            throw GenerateQualityGate.failureException(gateErrors);
        }

        String resolvedSource = (source == null || source.isBlank()) ? "PASTE_IMPORT" : source;
        workbooks.saveFromCases(projectId, repaired, resolvedSource, projectId, model);
        if (coverageNotes != null && !coverageNotes.isBlank()) {
            workbooks.updateCoverageNotes(projectId, coverageNotes);
        }
        return toImportPayload(projectId, repaired, model, coverageNotes);
    }

    private record ParsedRaw(List<ManualTestCase> cases, String coverageNotes) {
    }

    private static ParsedRaw parseRaw(String stripped, String formatHint) {
        String hint = formatHint == null || formatHint.isBlank()
                ? "auto"
                : formatHint.trim().toLowerCase(Locale.ROOT);
        if ("json".equals(hint)) {
            GeneratedTcJsonParser.ParseResult parsed = GeneratedTcJsonParser.parse(stripped);
            return new ParsedRaw(parsed.cases(), parsed.coverageNotes());
        }
        if ("csv".equals(hint)) {
            return new ParsedRaw(
                    GeneratedTcCsvParser.parse(stripped),
                    GeneratedTcCsvParser.extractCoverageNotes(stripped));
        }
        String trimmed = stripped.trim();
        if (trimmed.startsWith("{")) {
            GeneratedTcJsonParser.ParseResult parsed = GeneratedTcJsonParser.parse(stripped);
            return new ParsedRaw(parsed.cases(), parsed.coverageNotes());
        }
        return new ParsedRaw(
                GeneratedTcCsvParser.parse(stripped),
                GeneratedTcCsvParser.extractCoverageNotes(stripped));
    }

    private static Map<String, Object> toImportPayload(
            String projectId,
            List<ManualTestCase> cases,
            String model,
            String coverageNotes
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        if (model != null && !model.isBlank()) {
            result.put("model", model);
        }
        result.put("coverageNotes", coverageNotes == null ? "" : coverageNotes);
        result.put("rows", toRowMaps(cases));
        result.put("csv", GeneratedTcCsvParser.toCsv(cases));
        result.put("counts", GeneratedTcCsvParser.countByKeelPath(cases));
        result.put("keelPathCounts", KeelPathCounts.from(cases).toMap());
        return result;
    }

    private static List<Map<String, Object>> toRowMaps(List<ManualTestCase> cases) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ManualTestCase tc : cases) {
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
            row.put("callBefore", tc.callBefore());
            rows.add(row);
        }
        return rows;
    }
}
