package delivery.portal.api;

import java.util.Map;

public record ApiError(String error, String message) {
    public Map<String, String> asMap() {
        return Map.of("error", error, "message", message == null ? "" : message);
    }
}
