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
@Table(name = "mobile_apps",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_apps_app_id", columnNames = "app_id"),
        indexes = {
                @Index(name = "idx_mobile_apps_tenant_project", columnList = "tenant_id,project_id")
        })
public class MobileAppEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    /** APK | INSTALLED */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "active_revision_id", length = 64)
    private String activeRevisionId;

    /** ACTIVE | TOMBSTONED */
    @Column(nullable = false, length = 32)
    private String lifecycle = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getActiveRevisionId() { return activeRevisionId; }
    public void setActiveRevisionId(String activeRevisionId) { this.activeRevisionId = activeRevisionId; }
    public String getLifecycle() { return lifecycle; }
    public void setLifecycle(String lifecycle) { this.lifecycle = lifecycle; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
