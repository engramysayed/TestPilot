package delivery.portal.api;

import delivery.portal.persistence.PortalUser;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.credits.AccountApprovalService;
import delivery.portal.service.credits.CreditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Authenticated self: balance + costs + approvalStatus. */
@RestController
@RequestMapping("/api/account")
public class AccountCreditsController {
    private final CreditService credits;
    private final CurrentUserService currentUser;

    public AccountCreditsController(CreditService credits, CurrentUserService currentUser) {
        this.credits = credits;
        this.currentUser = currentUser;
    }

    @GetMapping("/credits")
    public ResponseEntity<Map<String, Object>> myCredits() {
        // Prefer ResponseEntity over ResponseStatusException: PortalExceptionHandler's
        // catch-all Exception handler would otherwise map it to HTTP 500.
        if (!credits.costs().enabled()) {
            return ResponseEntity.notFound().build();
        }
        PortalUser me = currentUser.requireUser();
        Map<String, Object> out = new LinkedHashMap<>(credits.accountBalanceResponse(me.getId()));
        AccountApprovalService.putApprovalFields(out, me);
        return ResponseEntity.ok(out);
    }
}
