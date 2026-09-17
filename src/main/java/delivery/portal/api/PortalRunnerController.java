package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/runners")
public class PortalRunnerController {
    private final PortalStore store;
    private final CurrentUserService currentUser;

    public PortalRunnerController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record EnrollRequest(String label) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var runner : store.listWorkspaceRunners(projectId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", runner.id());
            item.put("label", runner.label());
            item.put("enrolledAt", runner.enrolledAt() == null ? "" : runner.enrolledAt().toString());
            item.put("revokedAt", runner.revokedAt() == null ? "" : runner.revokedAt().toString());
            item.put("lastHeartbeat", runner.lastHeartbeat() == null ? "" : runner.lastHeartbeat().toString());
            rows.add(item);
        }
        return ResponseEntity.ok(Map.of("runners", rows));
    }

    @PostMapping
    public ResponseEntity<?> enroll(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) EnrollRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        var created = store.enrollWorkspaceRunner(projectId, uid, body == null ? "" : body.label());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", created.id());
        out.put("token", created.token());
        out.put("message", "Copy this token now; it is not stored in plaintext");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @DeleteMapping("/{runnerId}")
    public ResponseEntity<?> revoke(
            @PathVariable("projectId") String projectId,
            @PathVariable("runnerId") String runnerId
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        store.revokeWorkspaceRunner(projectId, uid, runnerId);
        return ResponseEntity.ok(Map.of("status", "REVOKED", "id", runnerId));
    }
}
