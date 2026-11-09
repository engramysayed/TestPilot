package delivery.portal.api;

import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.credits.RefundRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin → Refunds. Account owner (head admin) only, like credit grants. */
@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundsController {

    private final RefundRequestService refunds;
    private final CurrentUserService currentUser;
    private final RolesService roles;

    public AdminRefundsController(RefundRequestService refunds, CurrentUserService currentUser, RolesService roles) {
        this.refunds = refunds;
        this.currentUser = currentUser;
        this.roles = roles;
    }

    public record DecisionBody(Long amount, String note) {
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "status", required = false, defaultValue = "PENDING") String status) {
        requireOwner();
        return Map.of("items", refunds.list(status), "counts", refunds.counts());
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        requireOwner();
        return refunds.counts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        requireOwner();
        return refunds.detail(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Refund request not found").asMap()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody(required = false) DecisionBody body) {
        requireOwner();
        try {
            return ResponseEntity.ok(refunds.approve(id, currentUser.requireUserId(),
                    body == null ? null : body.amount(), body == null ? null : body.note()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(new ApiError("CONFLICT", e.getMessage()).asMap());
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) DecisionBody body) {
        requireOwner();
        try {
            return ResponseEntity.ok(refunds.reject(id, currentUser.requireUserId(), body == null ? null : body.note()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(new ApiError("CONFLICT", e.getMessage()).asMap());
        }
    }

    private void requireOwner() {
        roles.requireHeadAdmin(currentUser.requireUser());
    }
}
