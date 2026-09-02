package delivery.portal.api;

import delivery.portal.service.AccessRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/access-requests")
public class AccessRequestController {
    private final AccessRequestService accessRequests;

    public AccessRequestController(AccessRequestService accessRequests) {
        this.accessRequests = accessRequests;
    }

    public record SubmitRequest(String email) {
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@RequestBody SubmitRequest body) {
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
