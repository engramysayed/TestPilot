package delivery.portal.api;

import delivery.portal.preflight.ProjectPreflightService;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/projects/{id}/preflight}: same access rule as {@code GET /api/projects/{id}}; 404 cross-tenant. */
@RestController
@RequestMapping("/api/projects")
public class ProjectPreflightController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final ProjectPreflightService preflight;

    public ProjectPreflightController(PortalStore store, CurrentUserService currentUser,
                                      ProjectPreflightService preflight) {
        this.store = store;
        this.currentUser = currentUser;
        this.preflight = preflight;
    }

    @GetMapping("/{projectId}/preflight")
    public ResponseEntity<?> preflight(@PathVariable("projectId") String projectId,
                                       @RequestParam(value = "credentialProfile", required = false) String credentialProfile,
                                       @RequestParam(value = "kind", required = false) String kind,
                                       @RequestParam(value = "deep", required = false, defaultValue = "false") boolean deep)
            throws Exception {
        Long uid = currentUser.requireUserId();
        var project = store.getOwnedProject(projectId, uid);
        if (project.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (kind != null && !kind.isBlank()
                && !java.util.Set.of("EXECUTE", "AUTOMATE", "HUNT").contains(kind.trim().toUpperCase(java.util.Locale.ROOT))) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "kind must be EXECUTE, AUTOMATE or HUNT").asMap());
        }
        return ResponseEntity.ok(preflight.run(projectId, project.get().getBaseUrl(), credentialProfile, kind, deep));
    }
}
