package delivery.portal.roles;

import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.roles.RoleEntity;
import delivery.portal.persistence.roles.RolePermissionEntity;
import delivery.portal.persistence.roles.RolePermissionRepository;
import delivery.portal.persistence.roles.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RolesService {
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final PortalUserRepository users;

    public RolesService(RoleRepository roles, RolePermissionRepository rolePermissions,
                        PortalUserRepository users) {
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.users = users;
    }

    @Transactional
    public void ensureSystemRolesSeeded() {
        seedIfMissing(SystemRoleNames.OWNER, "Account owner — all permissions", PermissionCodes.ownerAll());
        seedIfMissing(SystemRoleNames.ADMIN, "Admin — operate features + ops (no roles/credits manage by default)",
                PermissionCodes.adminDefaults());
        seedIfMissing(SystemRoleNames.MEMBER, "Member — read/execute within assigned projects",
                PermissionCodes.memberDefaults());
    }

    private void seedIfMissing(String name, String description, Set<String> perms) {
        if (roles.findByNameIgnoreCase(name).isPresent()) {
            return;
        }
        RoleEntity role = new RoleEntity();
        role.setRoleId(newRoleId());
        role.setName(name);
        role.setDescription(description);
        role.setSystem(true);
        role.setCreatedAt(Instant.now());
        roles.save(role);
        replacePermissions(role.getRoleId(), perms);
    }

    private static String newRoleId() {
        return "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public Optional<RoleEntity> findByRoleId(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return Optional.empty();
        }
        return roles.findByRoleId(roleId.trim());
    }

    public Optional<RoleEntity> findByName(String name) {
        return roles.findByNameIgnoreCase(name);
    }

    public List<String> permissionsForRoleId(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (RolePermissionEntity e : rolePermissions.findByRoleId(roleId)) {
            out.add(e.getPermission());
        }
        out.sort(String::compareTo);
        return out;
    }

    public Set<String> permissionSetForUser(PortalUser user) {
        ensureSystemRolesSeeded();
        String roleId = resolveRoleId(user);
        return new LinkedHashSet<>(permissionsForRoleId(roleId));
    }

    /** Resolve custom roleId; fall back to legacy PortalUser.Role → system role mapping. */
    public String resolveRoleId(PortalUser user) {
        ensureSystemRolesSeeded();
        if (user.getRoleId() != null && !user.getRoleId().isBlank()
                && roles.findByRoleId(user.getRoleId()).isPresent()) {
            return user.getRoleId();
        }
        String systemName = user.getRole() == PortalUser.Role.ADMIN
                ? SystemRoleNames.OWNER
                : SystemRoleNames.MEMBER;
        return roles.findByNameIgnoreCase(systemName).map(RoleEntity::getRoleId).orElse(null);
    }

    public boolean hasPermission(PortalUser user, String permissionCode) {
        String needed = PermissionCodes.normalize(permissionCode)
                .orElse(permissionCode == null ? "" : permissionCode.trim().toLowerCase(Locale.ROOT));
        return permissionSetForUser(user).contains(needed);
    }

    public void requirePermission(PortalUser user, String permissionCode) {
        if (!hasPermission(user, permissionCode)) {
            throw RoleRequiredException.missingPermission(
                    PermissionCodes.normalize(permissionCode).orElse(permissionCode));
        }
    }

    public void requireRolesManage(PortalUser actor) {
        requirePermission(actor, PermissionCodes.ROLES_MANAGE);
    }

    public List<Map<String, Object>> listRoles() {
        ensureSystemRolesSeeded();
        List<Map<String, Object>> out = new ArrayList<>();
        for (RoleEntity role : roles.findAllByOrderBySystemDescNameAsc()) {
            out.add(toRoleDto(role));
        }
        return out;
    }

    private Map<String, Object> toRoleDto(RoleEntity role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleId", role.getRoleId());
        row.put("name", role.getName());
        row.put("description", role.getDescription() == null ? "" : role.getDescription());
        row.put("system", role.isSystem());
        row.put("permissions", permissionsForRoleId(role.getRoleId()));
        row.put("userCount", countAssignedUsers(role.getRoleId()));
        row.put("createdAt", role.getCreatedAt() == null ? null : role.getCreatedAt().toString());
        return row;
    }

    /**
     * Users whose stored {@code role_id} is this role.
     * A null {@code role_id} is unassigned — it is not MEMBER (legacy USER) or OWNER
     * (legacy ADMIN). {@link #resolveRoleId} may still fall back for permission checks;
     * that fallback is not a membership count.
     */
    public long countAssignedUsers(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return 0L;
        }
        return users.countByRoleId(roleId.trim());
    }

    /**
     * Stored assignment for admin lists. Does not invent MEMBER/OWNER when {@code role_id} is null.
     */
    public Map<String, Object> assignmentFields(PortalUser user) {
        Map<String, Object> m = new LinkedHashMap<>();
        String stored = user == null ? null : user.getRoleId();
        RoleEntity role = (stored == null || stored.isBlank())
                ? null
                : roles.findByRoleId(stored.trim()).orElse(null);
        if (role == null) {
            m.put("assignedRoleId", null);
            m.put("roleId", null);
            m.put("roleName", "Unassigned");
            return m;
        }
        m.put("assignedRoleId", role.getRoleId());
        m.put("roleId", role.getRoleId());
        m.put("roleName", role.getName());
        return m;
    }

    @Transactional
    public Map<String, Object> createRole(String name, String description, List<String> permissions) {
        ensureSystemRolesSeeded();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String trimmed = name.trim();
        if (SystemRoleNames.isBuiltIn(trimmed)) {
            throw new IllegalArgumentException("Cannot create role with built-in name: " + trimmed);
        }
        if (roles.existsByNameIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Role name already exists");
        }
        Set<String> perms = PermissionCodes.normalizeAll(permissions);
        RoleEntity role = new RoleEntity();
        role.setRoleId(newRoleId());
        role.setName(trimmed);
        role.setDescription(description == null ? "" : description.trim());
        role.setSystem(false);
        role.setCreatedAt(Instant.now());
        roles.save(role);
        replacePermissions(role.getRoleId(), perms);
        return toRoleDto(role);
    }

    @Transactional
    public Map<String, Object> patchRole(String roleId, String name, String description,
                                         List<String> permissions) {
        ensureSystemRolesSeeded();
        RoleEntity role = roles.findByRoleId(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        if (role.isSystem()) {
            if (name != null && !name.isBlank() && !role.getName().equalsIgnoreCase(name.trim())) {
                throw RoleRequiredException.forbidden("System role name is locked");
            }
            // Permissions expandable for system roles (product allows)
            if (permissions != null) {
                Set<String> merged = new LinkedHashSet<>(permissionsForRoleId(role.getRoleId()));
                merged.addAll(PermissionCodes.normalizeAll(permissions));
                replacePermissions(role.getRoleId(), merged);
            }
            if (description != null) {
                role.setDescription(description);
                roles.save(role);
            }
            return toRoleDto(role);
        }
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            if (SystemRoleNames.isBuiltIn(trimmed)) {
                throw new IllegalArgumentException("Cannot rename to built-in name");
            }
            Optional<RoleEntity> clash = roles.findByNameIgnoreCase(trimmed);
            if (clash.isPresent() && !clash.get().getRoleId().equals(role.getRoleId())) {
                throw new IllegalArgumentException("Role name already exists");
            }
            role.setName(trimmed);
        }
        if (description != null) {
            role.setDescription(description);
        }
        roles.save(role);
        if (permissions != null) {
            replacePermissions(role.getRoleId(), PermissionCodes.normalizeAll(permissions));
        }
        return toRoleDto(role);
    }

    @Transactional
    public void deleteRole(String roleId) {
        ensureSystemRolesSeeded();
        RoleEntity role = roles.findByRoleId(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        if (role.isSystem()) {
            throw RoleRequiredException.forbidden("System roles cannot be deleted");
        }
        long assigned = users.findAllByOrderByIdAsc().stream()
                .filter(u -> roleId.equals(u.getRoleId()))
                .count();
        if (assigned > 0) {
            throw new IllegalStateException("ROLE_IN_USE");
        }
        rolePermissions.deleteByRoleId(roleId);
        roles.delete(role);
    }

    @Transactional
    public PortalUser assignRole(Long userId, String roleId) {
        ensureSystemRolesSeeded();
        RoleEntity role = roles.findByRoleId(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        PortalUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        applyRoleToUser(user, role);
        return users.save(user);
    }

    /**
     * Approve / lock / reject. When status=APPROVED, roleId is required (no default MEMBER);
     * role is assigned in the same transaction. Does not grant credits.
     */
    @Transactional
    public PortalUser setApproval(Long userId, String statusRaw, String roleId, String note,
                                  Long actorUserId) {
        ensureSystemRolesSeeded();
        ApprovalStatus status = ApprovalStatus.parse(statusRaw);
        if (status == ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("status must be APPROVED, LOCKED, or REJECTED");
        }
        // Validate APPROVED + roleId before user lookup so clients always get ROLE_REQUIRED
        // (ApiError.code) rather than Unknown user when roleId is omitted.
        if (status == ApprovalStatus.APPROVED && (roleId == null || roleId.isBlank())) {
            throw new RoleRequiredException("ROLE_REQUIRED",
                    "roleId is required when approving (no default MEMBER)");
        }
        PortalUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (status == ApprovalStatus.APPROVED) {
            RoleEntity role = roles.findByRoleId(roleId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role"));
            applyRoleToUser(user, role);
            user.setApprovalStatus(ApprovalStatus.APPROVED);
            user.setApprovedAt(Instant.now());
            user.setApprovedByUserId(actorUserId);
            // note intentionally unused for persistence in this slice (no secrets in logs)
            return users.save(user);
        }
        // LOCKED | REJECTED — roleId not required
        user.setApprovalStatus(status);
        if (status == ApprovalStatus.LOCKED || status == ApprovalStatus.REJECTED) {
            // keep existing roleId; do not clear
            user.setApprovedAt(null);
            user.setApprovedByUserId(actorUserId);
        }
        return users.save(user);
    }

    private void applyRoleToUser(PortalUser user, RoleEntity role) {
        user.setRoleId(role.getRoleId());
        // Keep Spring Security compatible: OWNER/ADMIN system → ROLE_ADMIN; else USER
        if (SystemRoleNames.OWNER.equalsIgnoreCase(role.getName())
                || SystemRoleNames.ADMIN.equalsIgnoreCase(role.getName())
                || permissionsForRoleId(role.getRoleId()).contains(PermissionCodes.ROLES_MANAGE)
                || permissionsForRoleId(role.getRoleId()).contains(PermissionCodes.ADMIN_OPS)
                || permissionsForRoleId(role.getRoleId()).contains(PermissionCodes.CREDITS_ADMIN)) {
            user.setRole(PortalUser.Role.ADMIN);
        } else {
            user.setRole(PortalUser.Role.USER);
        }
    }

    private void replacePermissions(String roleId, Set<String> perms) {
        rolePermissions.deleteByRoleId(roleId);
        for (String p : perms) {
            RolePermissionEntity e = new RolePermissionEntity();
            e.setRoleId(roleId);
            e.setPermission(p);
            rolePermissions.save(e);
        }
    }

    public Map<String, Object> mePermissions(PortalUser user) {
        ensureSystemRolesSeeded();
        String roleId = resolveRoleId(user);
        String roleName = roleId == null ? null
                : roles.findByRoleId(roleId).map(RoleEntity::getName).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleId", roleId);
        body.put("roleName", roleName);
        body.put("permissions", new ArrayList<>(permissionSetForUser(user)));
        body.put("approvalStatus", user.getApprovalStatus().name());
        body.put("approvedAt", user.getApprovedAt() == null ? null : user.getApprovedAt().toString());
        body.put("approvedByUserId", user.getApprovedByUserId());
        return body;
    }

    public Map<String, Object> userApprovalFields(PortalUser user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approvalStatus", user.getApprovalStatus().name());
        m.put("approvedAt", user.getApprovedAt() == null ? null : user.getApprovedAt().toString());
        m.put("approvedByUserId", user.getApprovedByUserId());
        m.put("roleId", resolveRoleId(user));
        return m;
    }
}
