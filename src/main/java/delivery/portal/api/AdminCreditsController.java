package delivery.portal.api;

import delivery.portal.persistence.PortalUser;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.credits.CreditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Platform ADMIN only (ROLE_ADMIN via /api/admin/**). */
@RestController
@RequestMapping("/api/admin/credits")
public class AdminCreditsController {
    private final CreditService credits;
    private final CurrentUserService currentUser;

    public AdminCreditsController(CreditService credits, CurrentUserService currentUser) {
        this.credits = credits;
        this.currentUser = currentUser;
    }

    @GetMapping("/costs")
    public Map<String, Object> costs() {
        requireAdmin();
        return credits.costs().asCostsDto();
    }

    @PostMapping("/{userId}")
    public ResponseEntity<?> setCredits(
            @PathVariable("userId") Long userId,
            @RequestBody SetCreditsRequest body) {
        requireAdmin();
        try {
            Map<String, Object> out = credits.setCredits(
                    userId,
                    body == null ? null : body.mode(),
                    body == null || body.amount() == null ? -1L : body.amount(),
                    body == null ? null : body.note(),
                    body == null ? null : body.idempotencyKey(),
                    currentUser.requireUserId());
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Unknown")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }
    }

    @GetMapping("/{userId}/ledger")
    public ResponseEntity<?> ledger(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "jobKind", required = false) String jobKind) {
        requireAdmin();
        try {
            return ResponseEntity.ok(credits.usageForUser(
                    userId, parseInstant(from), parseInstant(to), jobKind));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Unknown")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }
    }

    public record SetCreditsRequest(String mode, Long amount, String note, String idempotencyKey) {
    }

    private void requireAdmin() {
        if (currentUser.requireUser().getRole() != PortalUser.Role.ADMIN) {
            throw new SecurityException("ADMIN role required for credits admin");
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Instant.parse(raw.trim());
    }
}
