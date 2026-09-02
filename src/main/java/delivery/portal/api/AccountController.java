package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accounts;
    private final CurrentUserService currentUser;

    public AccountController(AccountService accounts, CurrentUserService currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> me() {
        var user = currentUser.requireUser();
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        );
    }

    /** Deprecated: targets moved to project config (baseUrl + credential profiles). */
    @GetMapping("/targets")
    public Map<String, Object> targets() {
        return Map.of("targets", List.of());
    }

    public record PasswordRequest(String currentPassword, String newPassword) {
    }

    public record EmailRequest(String newEmail, String currentPassword) {
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody PasswordRequest body) {
        accounts.changePassword(
                currentUser.requireUserId(),
                body == null ? null : body.currentPassword(),
                body == null ? null : body.newPassword()
        );
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Password updated"));
    }

    @PostMapping("/email")
    public ResponseEntity<Map<String, Object>> changeEmail(@RequestBody EmailRequest body) {
        var user = accounts.changeEmail(
                currentUser.requireUserId(),
                body == null ? null : body.newEmail(),
                body == null ? null : body.currentPassword()
        );
        // Update SecurityContext username for subsequent requests in this session is limited;
        // ask client to re-login after email change.
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "email", user.getEmail(),
                "message", "Email updated — please sign in again"
        ));
    }
}
