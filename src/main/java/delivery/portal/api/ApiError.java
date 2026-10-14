package delivery.portal.api;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApiError(String error, String message) {
    public Map<String, String> asMap() {
        return Map.of("error", error, "message", message == null ? "" : message);
    }

    /** Prefer for QUALITY_GATE and other structured envelopes (Frontend COORDINATE_WITH_AE). */
    public Map<String, Object> asObjectMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message == null ? "" : message);
        return m;
    }

    public static Map<String, Object> qualityGate(Map<String, Object> structured) {
        if (structured == null) {
            return new ApiError("QUALITY_GATE", "").asObjectMap();
        }
        Map<String, Object> m = new LinkedHashMap<>(structured);
        m.putIfAbsent("error", "QUALITY_GATE");
        m.putIfAbsent("code", "QUALITY_GATE");
        Object msg = m.get("message");
        if (msg == null) {
            msg = m.get("detail");
        }
        m.put("message", msg == null ? "" : String.valueOf(msg));
        m.putIfAbsent("detail", m.get("message"));
        return m;
    }
}
