package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.SuiteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists registered {@link delivery.portal.alerts.AlertChannel} beans (Email/Telegram stubs).
 */
@RestController
@RequestMapping("/api/alert-channels")
public class AlertChannelController {
    private final SuiteService suites;
    private final CurrentUserService currentUser;

    public AlertChannelController(SuiteService suites, CurrentUserService currentUser) {
        this.suites = suites;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        currentUser.requireUserId();
        try {
            return ResponseEntity.ok(suites.listAlertChannels());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", msg).asMap());
        }
    }
}
