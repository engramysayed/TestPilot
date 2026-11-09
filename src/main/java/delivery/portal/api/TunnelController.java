package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.tunnel.TunnelEnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/tunnel")
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final TunnelEnrollmentService tunnel;

    public TunnelController(
            PortalStore store,
            CurrentUserService currentUser,
            @Autowired(required = false) TunnelEnrollmentService tunnel
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.tunnel = tunnel;
    }

    /** Optional: present current token + agentSecret to reuse the enrollment instead of minting a new one. */
    public record EnrollBody(String reuseToken, String agentSecret) {
    }

    @PostMapping(path = "/enroll", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> enroll(
            @PathVariable String projectId,
            @RequestBody(required = false) EnrollBody body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (tunnel == null) {
            return ResponseEntity.status(503).body(new ApiError("TUNNEL_DISABLED", "Tunnel service not available").asMap());
        }
        return ResponseEntity.ok(tunnel.enroll(
                projectId,
                String.valueOf(currentUser.requireUserId()),
                body == null ? null : body.reuseToken(),
                body == null ? null : body.agentSecret()));
    }

    @GetMapping("/status/{token}")
    public ResponseEntity<?> status(@PathVariable String projectId, @PathVariable String token) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (tunnel == null) {
            return ResponseEntity.status(503).body(new ApiError("TUNNEL_DISABLED", "Tunnel service not available").asMap());
        }
        return ResponseEntity.ok(tunnel.status(token));
    }
}
