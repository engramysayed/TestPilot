package delivery.portal.api;

import delivery.portal.roles.PermissionCodes;
import delivery.portal.roles.RoleRequiredException;
import delivery.portal.roles.RolesService;
import delivery.portal.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminRolesController {
    private final RolesService roles;
    private final CurrentUserService currentUser;

    public AdminRolesController(RolesService roles, CurrentUserService currentUser) {
        this.roles = roles;
        this.currentUser = currentUser;
    }

    @GetMapping("/roles")
    public Map<String, Object> list() {
        requireOwner();
        return Map.of("roles", roles.listRoles());
    }

    public record CreateRoleRequest(String name, String description, List<String> permissions) {
    }

    @PostMapping("/roles")
    public ResponseEntity<?> create(@RequestBody CreateRoleRequest body) {
        requireOwner();
        Map<String, Object> created = roles.createRole(
                body == null ? null : body.name(),
                body == null ? null : body.description(),
                body == null ? null : body.permissions());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    public record PatchRoleRequest(String name, String description, List<String> permissions) {
    }

    @PatchMapping("/roles/{roleId}")
    public Map<String, Object> patch(@PathVariable("roleId") String roleId,
                                     @RequestBody PatchRoleRequest body) {
        requireOwner();
        return roles.patchRole(roleId,
                body == null ? null : body.name(),
                body == null ? null : body.description(),
                body == null ? null : body.permissions());
    }

    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<?> delete(@PathVariable("roleId") String roleId) {
        requireOwner();
        try {
            roles.deleteRole(roleId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalStateException e) {
            if ("ROLE_IN_USE".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("CONFLICT", "Role still assigned to users").asMap());
            }
            throw e;
        }
    }

    public record AssignRoleRequest(String roleId) {
    }

    @PostMapping("/users/{userId}/role")
    public Map<String, Object> assignRole(@PathVariable("userId") Long userId,
                                         @RequestBody AssignRoleRequest body) {
        requireOwner();
        var user = roles.assignRole(userId, body == null ? null : body.roleId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", user.getId());
        out.put("roleId", user.getRoleId());
        out.put("approvalStatus", user.getApprovalStatus().name());
        return out;
    }

    /** Approve with required roleId when APPROVED; no credits grant; no default MEMBER. */
    public record ApprovalRequest(String status, String roleId, String note) {
    }

    @PostMapping("/users/{userId}/approval")
    public ResponseEntity<?> setApproval(@PathVariable("userId") Long userId,
                                         @RequestBody ApprovalRequest body) {
        requireOwner();
        try {
            var user = roles.setApproval(
                    userId,
                    body == null ? null : body.status(),
                    body == null ? null : body.roleId(),
                    body == null ? null : body.note(),
                    currentUser.requireUserId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", user.getId());
            out.put("approvalStatus", user.getApprovalStatus().name());
            out.put("roleId", user.getRoleId());
            out.put("approvedAt", user.getApprovedAt() == null ? null : user.getApprovedAt().toString());
            out.put("approvedByUserId", user.getApprovedByUserId());
            out.put("message", "APPROVED".equals(user.getApprovalStatus().name())
                    ? "Account approved. Set credits separately — nothing is added automatically."
                    : ("Account marked " + user.getApprovalStatus().name() + "."));
            return ResponseEntity.ok(out);
        } catch (RoleRequiredException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError(e.errorCode(), e.getMessage()).asMap());
        }
    }

    @GetMapping("/permissions")
    public Map<String, Object> permissionsCatalog() {
        requireOwner();
        return PermissionCodes.catalogPayload();
    }

    private void requireOwner() {
        roles.requireHeadAdmin(currentUser.requireUser());
    }
}
