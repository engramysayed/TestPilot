package delivery.authoring;

import org.json.JSONObject;

/** Parsed Cursor sidecar groundRank response. */
public record GroundRankResult(
        String candidateId,
        String confidence,
        String rationale
) {
    public static final GroundRankResult EMPTY = new GroundRankResult("", "low", "");

    public static GroundRankResult parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return EMPTY;
        }
        try {
            JSONObject body = new JSONObject(rawJson.trim());
            String id = body.optString("candidateId", "").trim();
            String confidence = body.optString("confidence", "low").trim().toLowerCase();
            String rationale = body.optString("rationale", "").trim();
            return new GroundRankResult(id, confidence.isBlank() ? "low" : confidence, rationale);
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    public boolean isAcceptable() {
        return isAcceptableAt("medium");
    }

    /** Admin-configurable floor: 'medium' accepts medium+high, 'high' accepts only high. */
    public boolean isAcceptableAt(String minConfidence) {
        if (candidateId == null || candidateId.isBlank()) return false;
        String floor = minConfidence == null ? "medium" : minConfidence.trim().toLowerCase();
        if ("high".equals(floor)) {
            return "high".equals(confidence);
        }
        return "high".equals(confidence) || "medium".equals(confidence);
    }
}
