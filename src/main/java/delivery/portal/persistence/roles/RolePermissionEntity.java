package delivery.portal.persistence.roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "role_permissions", uniqueConstraints = @UniqueConstraint(
        name = "uk_role_permission", columnNames = {"role_id", "permission"}))
public class RolePermissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;

    @Column(nullable = false, length = 64)
    private String permission;

    public Long getId() { return id; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
