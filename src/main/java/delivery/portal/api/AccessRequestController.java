package delivery.portal.api;

import delivery.portal.security.LoginThrottle;
import delivery.portal.service.AccessRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/access-requests")
public class AccessRequestController {
    private final AccessRequestService accessRequests;
    private final LoginThrottle throttle;

    public AccessRequestController(AccessRequestService accessRequests,
                                   @Qualifier("accessRequestThrottle") LoginThrottle throttle) {
        this.accessRequests = accessRequests;
        this.throttle = throttle;
    }

    public record SubmitRequest(String email) {
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@RequestBody SubmitRequest body, HttpServletRequest request) {
        // Public endpoint — cap per-IP to stop flood of distinct emails filling the admin review queue.
        String key = LoginThrottle.key("access-request", request.getRemoteAddr());
        Instant now = Instant.now();
        if (!throttle.allow(key, now)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(throttle.retryAfterSeconds(key, now)))
                    .body(Map.of(
                            "status", "error",
                            "message", "Too many requests. Please try again later."
                    ));
        }
        // Every attempt counts toward the per-IP cap, whether it succeeds or not.
        throttle.recordFailure(key, now);
        try {
            accessRequests.submit(body == null ? null : body.email());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "ok",
                    "message", "Request received. An admin will review it and email you a link to set your password."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage() == null ? "Invalid request" : e.getMessage()
            ));
        }
    }
}
