package delivery.visual;

import org.json.JSONObject;

import java.time.Instant;

/** meta.json beside baseline.png — Backend approve bumps {@code revision}. */
public record VisualBaselineMeta(
        String projectId,
        String caseKey,
        String viewport,
        int revision,
        String updatedAt,
        String sourceRunId,
        int width,
        int height
) {
    public static VisualBaselineMeta of(
            String projectId,
            String caseKey,
            String viewport,
            int revision,
            String sourceRunId,
            int width,
            int height
    ) {
        return new VisualBaselineMeta(
                projectId == null ? "" : projectId,
                caseKey == null ? "" : caseKey,
                VisualBaselinePaths.sanitizeViewport(viewport),
                Math.max(1, revision),
                Instant.now().toString(),
                sourceRunId == null ? "" : sourceRunId,
                Math.max(0, width),
                Math.max(0, height));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("projectId", projectId == null ? "" : projectId);
        o.put("caseKey", caseKey == null ? "" : caseKey);
        o.put("viewport", viewport == null ? "" : viewport);
        o.put("revision", revision);
        o.put("updatedAt", updatedAt == null ? "" : updatedAt);
        if (sourceRunId != null && !sourceRunId.isBlank()) {
            o.put("sourceRunId", sourceRunId);
        }
        o.put("width", width);
        o.put("height", height);
        return o;
    }

    public static VisualBaselineMeta fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JSONObject o = new JSONObject(raw);
        return new VisualBaselineMeta(
                o.optString("projectId", ""),
                o.optString("caseKey", ""),
                VisualBaselinePaths.sanitizeViewport(o.optString("viewport", VisualDiffConfig.DEFAULT_VIEWPORT)),
                Math.max(1, o.optInt("revision", 1)),
                o.optString("updatedAt", ""),
                o.optString("sourceRunId", ""),
                Math.max(0, o.optInt("width", 0)),
                Math.max(0, o.optInt("height", 0)));
    }
}
