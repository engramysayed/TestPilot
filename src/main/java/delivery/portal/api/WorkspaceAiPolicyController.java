package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.WorkspaceAiPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-policy")
public class WorkspaceAiPolicyController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final WorkspaceAiPolicyService policy;

    public WorkspaceAiPolicyController(PortalStore store, CurrentUserService currentUser,
                                       WorkspaceAiPolicyService policy) {
        this.store = store;
        this.currentUser = currentUser;
        this.policy = policy;
    }

    public record PolicyBody(
            boolean aiValidationEnabled,
            boolean tcValidationEnabled,
            boolean frameworkRepairEnabled,
            boolean failureInvestigationEnabled,
            boolean huntValidationEnabled
    ) {}

    @GetMapping
    public ResponseEntity<?> get(@PathVariable String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        WorkspaceAiPolicyService.Policy p = policy.load(projectId);
        return ResponseEntity.ok(new PolicyBody(
                p.aiValidationEnabled(), p.tcValidationEnabled(), p.frameworkRepairEnabled(),
                p.failureInvestigationEnabled(), p.huntValidationEnabled()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> set(@PathVariable String projectId, @RequestBody PolicyBody body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (body == null) return bad("body required");
        try {
            policy.save(projectId, new WorkspaceAiPolicyService.Policy(
                    body.aiValidationEnabled(), body.tcValidationEnabled(), body.frameworkRepairEnabled(),
                    body.failureInvestigationEnabled(), body.huntValidationEnabled()));
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "error" : e.getMessage()).asMap());
        }
    }

    private static ResponseEntity<?> bad(String msg) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", msg).asMap());
    }
}
