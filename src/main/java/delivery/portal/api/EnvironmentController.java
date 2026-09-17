package delivery.portal.api;

import delivery.env.EnvironmentProfile;
import delivery.env.EnvironmentStore;
import delivery.identity.TenantId;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/projects/{projectId}/environments")
public class EnvironmentController {
    private final PortalStore store;
    private final CurrentUserService currentUser;

    public EnvironmentController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record EnvironmentRequest(
            String name,
            String origin,
            String credentialProfileRef,
            String providerAllowlist,
            Integer maxQueued,
            Integer maxRunning,
            String baseRevision
    ) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        EnvironmentStore envs = new EnvironmentStore(store.environmentRoot(projectId));
        List<Map<String, Object>> rows = new ArrayList<>();
        String head = envs.head().map(EnvironmentStore.Revision::id).orElse("");
        for (var rev : envs.list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rev.id());
            row.put("parentId", rev.parentId());
            row.put("name", rev.name());
            row.put("createdAt", rev.createdAt().toString());
            row.put("head", rev.id().equals(head));
            row.put("origin", rev.profile().origin());
            row.put("credentialProfileRef", rev.profile().credentialProfileRef());
            row.put("providerAllowlist", rev.profile().providerAllowlist());
            rows.add(row);
        }
        return ResponseEntity.ok(Map.of("environments", rows, "tenantId",
                store.getOwnedProject(projectId, uid).map(p -> p.getTenantId()).orElse("")));
    }

    @PostMapping
    public ResponseEntity<?> commit(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) EnvironmentRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "body is required").asMap());
        }
        EnvironmentProfile profile = new EnvironmentProfile(
                body.name(),
                body.origin(),
                body.credentialProfileRef(),
                body.providerAllowlist(),
                body.maxQueued() == null ? 0 : body.maxQueued(),
                body.maxRunning() == null ? 0 : body.maxRunning());
        var rev = new EnvironmentStore(store.environmentRoot(projectId)).commit(body.baseRevision(), profile);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", rev.id());
        out.put("parentId", rev.parentId());
        out.put("origin", rev.profile().origin());
        out.put("name", rev.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @GetMapping("/{fromId}/diff/{toId}")
    public ResponseEntity<?> diff(
            @PathVariable("projectId") String projectId,
            @PathVariable("fromId") String fromId,
            @PathVariable("toId") String toId
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        EnvironmentStore envs = new EnvironmentStore(store.environmentRoot(projectId));
        var from = envs.read(fromId);
        var to = envs.read(toId);
        return ResponseEntity.ok(Map.of(
                "from", from.id(),
                "to", to.id(),
                "changed", EnvironmentStore.compare(from.profile(), to.profile()),
                "storageIdentity", TenantId.parse(
                        store.getOwnedProject(projectId, uid).orElseThrow().getTenantId()).value()
        ));
    }
}
