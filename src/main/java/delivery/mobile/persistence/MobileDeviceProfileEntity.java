package delivery.mobile.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "mobile_device_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_device_profiles_rev", columnNames = "profile_revision_id"),
        indexes = @Index(name = "idx_mobile_device_profiles_tenant_project", columnList = "tenant_id,project_id"))
public class MobileDeviceProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_revision_id", nullable = false, length = 64)
    private String profileRevisionId;

    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "runner_id", length = 64)
    private String runnerId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "app_revision_id", length = 64)
    private String appRevisionId;

    @Column(name = "package_name", length = 256)
    private String packageName;

    @Column(name = "capabilities_allowlist_json", length = 4000)
    private String capabilitiesAllowlistJson;

    @Column(name = "reset_permission_policy_json", length = 4000)
    private String resetPermissionPolicyJson;

    @Column(length = 64)
    private String locale;

    @Column(length = 32)
    private String orientation;

    @Column(name = "timeouts_json", length = 2000)
    private String timeoutsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getProfileRevisionId() { return profileRevisionId; }
    public void setProfileRevisionId(String profileRevisionId) { this.profileRevisionId = profileRevisionId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRunnerId() { return runnerId; }
    public void setRunnerId(String runnerId) { this.runnerId = runnerId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getAppRevisionId() { return appRevisionId; }
    public void setAppRevisionId(String appRevisionId) { this.appRevisionId = appRevisionId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getCapabilitiesAllowlistJson() { return capabilitiesAllowlistJson; }
    public void setCapabilitiesAllowlistJson(String capabilitiesAllowlistJson) { this.capabilitiesAllowlistJson = capabilitiesAllowlistJson; }
    public String getResetPermissionPolicyJson() { return resetPermissionPolicyJson; }
    public void setResetPermissionPolicyJson(String resetPermissionPolicyJson) { this.resetPermissionPolicyJson = resetPermissionPolicyJson; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
    public String getTimeoutsJson() { return timeoutsJson; }
    public void setTimeoutsJson(String timeoutsJson) { this.timeoutsJson = timeoutsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
