package delivery.portal.api;

import delivery.excel.GenerateQualityGate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE-D10: structured QUALITY_GATE HTTP bodies (Frontend COORDINATE_WITH_AE / 0057 toApiMap).
 * Prefer this over {@code new ApiError("QUALITY_GATE", flatDetail).asMap()}.
 */
public final class QualityGateResponses {
    private QualityGateResponses() {
    }

    public static Map<String, Object> fromException(IllegalArgumentException e) {
        String detail = GenerateQualityGate.isQualityGateFailure(e)
                ? GenerateQualityGate.qualityGateDetail(e)
                : (e.getMessage() == null ? "" : e.getMessage());
        return fromDetail(detail);
    }

    public static Map<String, Object> fromErrors(List<String> errors) {
        List<String> parts = errors == null ? List.of() : errors;
        Map<String, Object> body = GenerateQualityGate.toApiMap(parts, List.of());
        return withDefaultSuggestion(ApiError.qualityGate(body));
    }

    public static Map<String, Object> fromDetail(String detail) {
        ArrayList<String> parts = new ArrayList<>();
        if (detail != null && !detail.isBlank()) {
            for (String part : detail.split(";")) {
                String t = part.trim();
                if (!t.isBlank()) {
                    parts.add(t);
                }
            }
        }
        return fromErrors(parts);
    }

    private static Map<String, Object> withDefaultSuggestion(Map<String, Object> body) {
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("keelPath", "EXECUTE");
        suggestion.put("technique", "Fix authoring issues, then re-check KeelPath (prefer EXECUTE when unsure)");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) body.get("suggestions");
        if (suggestions == null) {
            suggestions = new ArrayList<>();
            body.put("suggestions", suggestions);
        } else {
            suggestions = new ArrayList<>(suggestions);
            body.put("suggestions", suggestions);
        }
        suggestions.add(suggestion);
        return body;
    }
}
