package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.SuiteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AE/portal claim hook: suites whose schedule.nextRunAt is due.
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleDueController {
    private final SuiteService suites;
    private final CurrentUserService currentUser;

    public ScheduleDueController(SuiteService suites, CurrentUserService currentUser) {
        this.suites = suites;
        this.currentUser = currentUser;
    }

    @GetMapping("/due")
    public ResponseEntity<?> due(
            @RequestParam(value = "withinSeconds", required = false, defaultValue = "60") int withinSeconds
    ) {
        Long userId = currentUser.requireUserId();
        try {
            int within = Math.max(0, withinSeconds);
            return ResponseEntity.ok(suites.listDue(userId, within));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", msg).asMap());
        }
    }
}
