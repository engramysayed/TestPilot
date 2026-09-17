package delivery.portal.api;

import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceMembership;
import delivery.identity.WorkspaceRole;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
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
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class WorkspaceCollaborationController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final PortalUserRepository users;

    public WorkspaceCollaborationController(
            PortalStore store, CurrentUserService currentUser, PortalUserRepository users) {
        this.store = store;
        this.currentUser = currentUser;
        this.users = users;
    }

    public record MemberRequest(String email, String role) {
    }

    public record TransferRequest(Long userId) {
    }

    public record ServiceRequest(String label, String role) {
    }

    @GetMapping("/members")
    public ResponseEntity<?> listMembers(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        delivery.identity.TenantId tenant = delivery.identity.TenantId.parse(
                store.getOwnedProject(projectId, uid).orElseThrow().getTenantId());
        List<Map<String, Object>> members = new ArrayList<>();
        for (WorkspaceMembership row : store.directory().listMembers(tenant)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", row.userId());
            item.put("role", row.role().name());
            item.put("email", users.findById(row.userId()).map(PortalUser::getEmail).orElse(""));
            members.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("members", members);
        body.put("workspaceRole", store.workspaceRole(projectId, uid));
        if (store.canAdminister(projectId, uid)) {
            List<Map<String, Object>> pending = new ArrayList<>();
            for (WorkspaceDirectory.PendingInvite invite : store.directory().pendingInvites(tenant)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("email", invite.email());
                item.put("role", invite.role().name());
                item.put("invitedBy", invite.invitedBy());
                item.put("at", invite.at());
                pending.add(item);
            }
            body.put("pending", pending);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/members")
    public ResponseEntity<?> addMember(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) MemberRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessAdmin(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        if (body == null || body.email() == null || body.email().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "email is required").asMap());
        }
        WorkspaceRole role = parseOperableRole(body.role());
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            store.addWorkspaceMember(projectId, uid, existing.get().getId(), role);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "ADDED",
                    "email", email,
                    "userId", existing.get().getId(),
                    "role", role.name()
            ));
        }
        store.inviteWorkspaceEmail(projectId, uid, email, role);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "INVITED",
                "email", email,
                "role", role.name()
        ));
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<?> removeMember(
            @PathVariable("projectId") String projectId,
            @PathVariable("userId") long userId
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessAdmin(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        int cancelled = store.removeWorkspaceMember(projectId, uid, userId);
        return ResponseEntity.ok(Map.of(
                "status", "REMOVED",
                "userId", userId,
                "cancelledJobs", cancelled
        ));
    }

    @PostMapping("/ownership")
    public ResponseEntity<?> transferOwnership(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) TransferRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessAdmin(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        if (body == null || body.userId() == null || body.userId() <= 0) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "userId is required").asMap());
        }
        store.transferWorkspaceOwnership(projectId, uid, body.userId());
        return ResponseEntity.ok(Map.of(
                "status", "TRANSFERRED",
                "ownerUserId", body.userId()
        ));
    }

    @GetMapping("/audit")
    public ResponseEntity<?> audit(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        String tenantId = store.getOwnedProject(projectId, uid).orElseThrow().getTenantId();
        List<Map<String, Object>> events = new ArrayList<>();
        for (WorkspaceDirectory.AuditEvent event : store.directory()
                .audit(delivery.identity.TenantId.parse(tenantId))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("at", event.at());
            item.put("actorUserId", event.actorUserId());
            item.put("action", event.action());
            item.put("targetUserId", event.targetUserId());
            item.put("detail", event.detail());
            events.add(item);
        }
        return ResponseEntity.ok(Map.of("events", events));
    }

    @GetMapping("/services")
    public ResponseEntity<?> listServices(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessAdmin(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        String tenantId = store.getOwnedProject(projectId, uid).orElseThrow().getTenantId();
        List<Map<String, Object>> services = new ArrayList<>();
        for (var svc : store.directory().listServices(delivery.identity.TenantId.parse(tenantId))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", svc.id());
            item.put("role", svc.role().name());
            item.put("label", svc.label());
            item.put("revoked", svc.revoked());
            services.add(item);
        }
        return ResponseEntity.ok(Map.of("services", services));
    }

    @PostMapping("/services")
    public ResponseEntity<?> createService(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) ServiceRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessAdmin(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        WorkspaceRole role = parseOperableRole(body == null ? null : body.role());
        var created = store.createWorkspaceService(
                projectId, uid, role, body == null ? "" : body.label());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", created.id());
        out.put("role", created.role().name());
        out.put("token", created.token());
        out.put("message", "Copy this token now; it is not stored in plaintext");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @DeleteMapping("/services/{serviceId}")
    public ResponseEntity<?> revokeService(
            @PathVariable("projectId") String projectId,
            @PathVariable("serviceId") String serviceId
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessAdmin(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        store.revokeWorkspaceService(projectId, uid, serviceId);
        return ResponseEntity.ok(Map.of("status", "REVOKED", "id", serviceId));
    }

    private static WorkspaceRole parseOperableRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return WorkspaceRole.MEMBER;
        }
        WorkspaceRole role = WorkspaceRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        if (role == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("use transferOwnership to grant OWNER");
        }
        return role;
    }
}
