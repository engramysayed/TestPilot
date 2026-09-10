package delivery.portal.api;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/** Maps CallBefore expander failures to HTTP 400 {@link ApiError} responses. */
public final class CallBeforeApiErrors {
    private CallBeforeApiErrors() {
    }

    public static ResponseEntity<Map<String, String>> badRequestOrNull(IllegalArgumentException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return null;
        }
        String code = null;
        if (msg.startsWith("UNKNOWN_TC:")) {
            code = "UNKNOWN_TC";
        } else if (msg.startsWith("UNKNOWN_CALL_BEFORE:")) {
            code = "UNKNOWN_CALL_BEFORE";
        } else if (msg.startsWith("CALL_BEFORE_SELF:")) {
            code = "CALL_BEFORE_SELF";
        } else if (msg.startsWith("CALL_BEFORE_CYCLE:")) {
            code = "CALL_BEFORE_CYCLE";
        }
        if (code == null) {
            return null;
        }
        return ResponseEntity.badRequest().body(new ApiError(code, msg).asMap());
    }
}
