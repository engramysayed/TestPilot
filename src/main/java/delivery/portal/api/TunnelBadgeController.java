package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.tunnel.TunnelProjectStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/tunnel")
public class TunnelBadgeController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final TunnelProjectStatusService tunnelStatus;

    public TunnelBadgeController(
            PortalStore store,
            CurrentUserService currentUser,
            TunnelProjectStatusService tunnelStatus
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.tunnelStatus = tunnelStatus;
    }

    @GetMapping("/badge")
    public ResponseEntity<?> badge(@PathVariable String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(tunnelStatus.badge(projectId));
    }
}
