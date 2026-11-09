package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.tunnel.TunnelEnrollmentDiskStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/tunnel")
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelHeartbeatController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();

    public TunnelHeartbeatController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record HeartbeatBody(String token, String listenUrl) {
    }

    @PostMapping(path = "/heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> heartbeat(
            @PathVariable String projectId,
            @RequestBody(required = false) HeartbeatBody body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        String token = body == null ? "" : (body.token() == null ? "" : body.token().trim());
        Optional<Map<String, Object>> latest = disk.latest(store.projectDiskRoot(projectId));
        if (latest.isEmpty() || token.isBlank() || !token.equals(String.valueOf(latest.get().getOrDefault("token", "")))) {
            return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Unknown tunnel token").asMap());
        }
        disk.markActive(store.projectDiskRoot(projectId), body == null ? "" : body.listenUrl());
        return ResponseEntity.ok(Map.of("status", "active", "projectId", projectId));
    }
}
