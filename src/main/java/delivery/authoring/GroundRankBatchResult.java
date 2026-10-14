package delivery.authoring;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Parsed Cursor sidecar groundRankBatch response. */
public record GroundRankBatchResult(List<Pick> picks, String rationale) {

    public static final GroundRankBatchResult EMPTY = new GroundRankBatchResult(List.of(), "");

    public record Pick(int intentIndex, String candidateId, String confidence) {
        public boolean isAcceptable() {
            return ("high".equalsIgnoreCase(confidence) || "medium".equalsIgnoreCase(confidence))
                    && candidateId != null && !candidateId.isBlank();
        }
    }

    public static GroundRankBatchResult parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return EMPTY;
        }
        try {
            JSONObject body = new JSONObject(rawJson.trim());
            String rationale = body.optString("rationale", "").trim();
            JSONArray arr = body.optJSONArray("picks");
            if (arr == null) {
                return new GroundRankBatchResult(List.of(), rationale);
            }
            List<Pick> picks = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                int idx = row.optInt("intentIndex", row.optInt("intent", i));
                String id = row.optString("candidateId", "").trim();
                String confidence = row.optString("confidence", "low").trim().toLowerCase();
                picks.add(new Pick(idx, id, confidence.isBlank() ? "low" : confidence));
            }
            return new GroundRankBatchResult(picks, rationale);
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    /** First N picks must be consecutive 0..N-1 with acceptable confidence. */
    public List<Pick> consecutiveAcceptableFromZero() {
        List<Pick> out = new ArrayList<>();
        for (int expected = 0; ; expected++) {
            Pick found = null;
            for (Pick p : picks) {
                if (p.intentIndex() == expected) {
                    found = p;
                    break;
                }
            }
            if (found == null || !found.isAcceptable()) {
                break;
            }
            out.add(found);
        }
        return out;
    }
}
