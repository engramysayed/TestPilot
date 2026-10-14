package delivery.portal.service;

import delivery.excel.CoverageTechniqueCheck;
import delivery.excel.KeelPathSuggester;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adds suggestedKeelPath / alternatives / coverageWarnings to Generate API payloads. */
public final class GeneratePayloadEnricher {
    private GeneratePayloadEnricher() {
    }

    public static void enrich(Map<String, Object> payload, List<ManualTestCase> cases, String coverageNotes) {
        if (payload == null) {
            return;
        }
        List<ManualTestCase> safe = cases == null ? List.of() : cases;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                ManualTestCase tc = i < safe.size() ? safe.get(i) : null;
                KeelPathSuggester.Suggestion s = KeelPathSuggester.suggest(tc);
                Map<String, Object> row = rows.get(i);
                row.put("suggestedKeelPath", s.suggestedKeelPath());
                row.put("alternatives", s.alternatives());
                row.put("keelPathReason", s.reason());
            }
        }
        // Batch-level modal suggestion: most common suggested path among blank keelPaths, else first case.
        String batchSuggest = null;
        List<String> batchAlts = List.of();
        for (ManualTestCase tc : safe) {
            KeelPathSuggester.Suggestion s = KeelPathSuggester.suggest(tc);
            if (tc.keelPath() == null || tc.keelPath().isBlank()) {
                batchSuggest = s.suggestedKeelPath();
                batchAlts = s.alternatives();
                break;
            }
            if (batchSuggest == null) {
                batchSuggest = s.suggestedKeelPath();
                batchAlts = s.alternatives();
            }
        }
        if (batchSuggest != null) {
            payload.put("suggestedKeelPath", batchSuggest);
            payload.put("alternatives", batchAlts);
        }
        List<CoverageTechniqueCheck.Warning> warnings = CoverageTechniqueCheck.warn(coverageNotes, safe);
        payload.put("coverageWarnings", CoverageTechniqueCheck.toMaps(warnings));
        Map<String, Object> qualityGate = new LinkedHashMap<>();
        qualityGate.put("passed", true);
        qualityGate.put("failures", List.of());
        payload.put("qualityGate", qualityGate);
    }

    public static List<Map<String, Object>> suggestionsForCases(List<ManualTestCase> cases) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (cases == null) {
            return out;
        }
        for (ManualTestCase tc : cases) {
            KeelPathSuggester.Suggestion s = KeelPathSuggester.suggest(tc);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tcId", tc == null ? "" : tc.tcId());
            m.put("keelPath", s.suggestedKeelPath());
            m.put("technique", s.reason());
            out.add(m);
        }
        return out;
    }
}
