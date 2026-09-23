package delivery.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobId;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private String status;

    private int passedCount;
    private int todoCount;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int progressCurrent;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int progressTotal;

    @Column(length = 1024)
    private String message;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    @Column(length = 1024)
    private String zipPath;

    @Column(length = 2048)
    private String baseUrl;

    @Column(length = 2048)
    private String excelPath;

    @Column(length = 2048)
    private String usernameCipher;

    @Column(length = 2048)
    private String passwordCipher;

    @Column(length = 16, columnDefinition = "varchar(16) default 'CONVERT'")
    private String jobKind = "CONVERT";

    @Column(length = 128)
    private String generateModel;

    @Column(name = "tenant_id", length = 40)
    private String tenantId;

    @Column(name = "attempt_id", length = 64)
    private String attemptId;

    @Column(name = "worker_id", length = 64)
    private String workerId;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "cancel_generation", nullable = false, columnDefinition = "integer default 0")
    private int cancelGeneration;

    @Column(name = "claim_stage", length = 16)
    private String claimStage;

    @Column(name = "input_snapshot_hash", length = 64)
    private String inputSnapshotHash;

    @Column(name = "provider_allowlist_snapshot", length = 128)
    private String providerAllowlistSnapshot;

    @Column(name = "library_revision_id", length = 64)
    private String libraryRevisionId;

    @Column(name = "environment_revision_id", length = 64)
    private String environmentRevisionId;

    @Column(name = "parent_job_id", length = 80)
    private String parentJobId;

    @Column(name = "precision_max_snapshot", nullable = false, columnDefinition = "integer default 0")
    private int precisionMaxSnapshot;
    private Boolean precisionEnabledSnapshot;
    @Column(length = 32)
    private String authoringEngineSnapshot;

    @Column(name = "require_private_runner", nullable = false, columnDefinition = "boolean default false")
    private boolean requirePrivateRunner;

    @Column(name = "providers_used", length = 128)
    private String providersUsed;

    @Column(name = "fallback_used", nullable = false, columnDefinition = "boolean default false")
    private boolean fallbackUsed;

    @Column(name = "fallback_reason", length = 256)
    private String fallbackReason;

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPassedCount() { return passedCount; }
    public void setPassedCount(int passedCount) { this.passedCount = passedCount; }
    public int getTodoCount() { return todoCount; }
    public void setTodoCount(int todoCount) { this.todoCount = todoCount; }
    public int getProgressCurrent() { return progressCurrent; }
    public void setProgressCurrent(int progressCurrent) { this.progressCurrent = progressCurrent; }
    public int getProgressTotal() { return progressTotal; }
    public void setProgressTotal(int progressTotal) { this.progressTotal = progressTotal; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getZipPath() { return zipPath; }
    public void setZipPath(String zipPath) { this.zipPath = zipPath; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getExcelPath() { return excelPath; }
    public void setExcelPath(String excelPath) { this.excelPath = excelPath; }
    public String getUsernameCipher() { return usernameCipher; }
    public void setUsernameCipher(String usernameCipher) { this.usernameCipher = usernameCipher; }
    public String getPasswordCipher() { return passwordCipher; }
    public void setPasswordCipher(String passwordCipher) { this.passwordCipher = passwordCipher; }
    public String getJobKind() { return jobKind; }
    public void setJobKind(String jobKind) { this.jobKind = jobKind; }
    public String getGenerateModel() { return generateModel; }
    public void setGenerateModel(String generateModel) { this.generateModel = generateModel; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public int getCancelGeneration() { return cancelGeneration; }
    public void setCancelGeneration(int cancelGeneration) { this.cancelGeneration = cancelGeneration; }
    public String getClaimStage() { return claimStage; }
    public void setClaimStage(String claimStage) { this.claimStage = claimStage; }
    public String getInputSnapshotHash() { return inputSnapshotHash; }
    public void setInputSnapshotHash(String inputSnapshotHash) { this.inputSnapshotHash = inputSnapshotHash; }
    public String getProviderAllowlistSnapshot() { return providerAllowlistSnapshot; }
    public void setProviderAllowlistSnapshot(String providerAllowlistSnapshot) {
        this.providerAllowlistSnapshot = providerAllowlistSnapshot;
    }
    public String getLibraryRevisionId() { return libraryRevisionId; }
    public void setLibraryRevisionId(String libraryRevisionId) { this.libraryRevisionId = libraryRevisionId; }
    public String getEnvironmentRevisionId() { return environmentRevisionId; }
    public void setEnvironmentRevisionId(String environmentRevisionId) {
        this.environmentRevisionId = environmentRevisionId;
    }
    public String getParentJobId() { return parentJobId; }
    public void setParentJobId(String parentJobId) { this.parentJobId = parentJobId; }
    public String getAuthoringEngineSnapshot() { return authoringEngineSnapshot; }
    public void setAuthoringEngineSnapshot(String value) { authoringEngineSnapshot = value; }
    public Boolean getPrecisionEnabledSnapshot() { return precisionEnabledSnapshot; }
    public void setPrecisionEnabledSnapshot(Boolean value) { precisionEnabledSnapshot = value; }
    public int getPrecisionMaxSnapshot() { return precisionMaxSnapshot; }
    public void setPrecisionMaxSnapshot(int precisionMaxSnapshot) { this.precisionMaxSnapshot = precisionMaxSnapshot; }
    public boolean isRequirePrivateRunner() { return requirePrivateRunner; }
    public void setRequirePrivateRunner(boolean requirePrivateRunner) {
        this.requirePrivateRunner = requirePrivateRunner;
    }
    public String getProvidersUsed() { return providersUsed; }
    public void setProvidersUsed(String providersUsed) { this.providersUsed = providersUsed; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
}
