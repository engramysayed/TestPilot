package delivery.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private int latestVersion = 0;

    @Column(length = 2048)
    private String baseUrl;

    @Column(nullable = true)
    private Boolean archived = Boolean.FALSE;

    private Instant archivedAt;

    /** keel | precision — default keel when null/blank */
    @Column(length = 32)
    private String authoringEngine;

    /** Per-job Cursor call cap when Precision is selected; null = server default */
    private Integer precisionMaxCallsPerJob;

    /** Opaque workspace id; membership is stored separately. */
    @Column(name = "tenant_id", length = 40)
    private String tenantId;

    public Long getId() { return id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public int getLatestVersion() { return latestVersion; }
    public void setLatestVersion(int latestVersion) { this.latestVersion = latestVersion; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public boolean isArchived() { return Boolean.TRUE.equals(archived); }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public String getAuthoringEngine() { return authoringEngine; }
    public void setAuthoringEngine(String authoringEngine) { this.authoringEngine = authoringEngine; }
    public Integer getPrecisionMaxCallsPerJob() { return precisionMaxCallsPerJob; }
    public void setPrecisionMaxCallsPerJob(Integer precisionMaxCallsPerJob) {
        this.precisionMaxCallsPerJob = precisionMaxCallsPerJob;
    }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
