package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.credits.RefundRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Job owner asks for (or checks) a refund on a failed charged run. */
@RestController
@RequestMapping("/api/jobs/{jobId}/refund-request")
public class JobRefundController {

    private final RefundRequestService refunds;
    private final CurrentUserService currentUser;

    public JobRefundController(RefundRequestService refunds, CurrentUserService currentUser) {
        this.refunds = refunds;
        this.currentUser = currentUser;
    }

    public record RequestBodyDto(String note) {
    }

    @GetMapping
    public ResponseEntity<?> get(@PathVariable String jobId) {
        return refunds.forJob(jobId, currentUser.requireUserId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "No refund request for this run").asMap()));
    }

    @PostMapping
    public ResponseEntity<?> request(@PathVariable String jobId, @RequestBody(required = false) RequestBodyDto body) {
        var out = refunds.requestByOwner(jobId, currentUser.requireUserId(), body == null ? null : body.note());
        if (!out.ok()) {
            int status = "NOT_FOUND".equals(out.code()) ? 404 : 409;
            return ResponseEntity.status(status).body(new ApiError(out.code(), out.message()).asMap());
        }
        return ResponseEntity.ok(Map.of("code", out.code(), "message", out.message(), "request", out.request()));
    }
}
