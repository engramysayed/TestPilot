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
@Table(name = "mobile_device_leases",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mobile_device_leases_lease_id", columnNames = "lease_id"),
                @UniqueConstraint(name = "uk_mobile_device_leases_active_device", columnNames = "device_id")
        },
        indexes = {
                @Index(name = "idx_mobile_device_leases_job", columnList = "job_id"),
                @Index(name = "idx_mobile_device_leases_tenant", columnList = "tenant_id")
        })
public class MobileDeviceLeaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lease_id", nullable = false, length = 64)
    private String leaseId;

    /** At most one active lease row per device (unique). Cleared/replaced on release. */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "attempt_id", length = 64)
    private String attemptId;

    /** Frozen admission input hash bound at claim time. */
    @Column(name = "input_hash", length = 128)
    private String inputHash;

    @Column(nullable = false)
    private long generation = 0L;

    @Column(name = "fence_token", nullable = false)
    private long fenceToken = 0L;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "session_ref", length = 128)
    private String sessionRef;

    @Column(name = "owned_process_ref", length = 128)
    private String ownedProcessRef;

    /** PENDING | ACKED | UNCERTAIN | NONE */
    @Column(name = "cleanup_status", nullable = false, length = 32)
    private String cleanupStatus = "NONE";

    /** RESERVED | ACTIVE | RELEASING | QUARANTINED */
    @Column(nullable = false, length = 32)
    private String state = "RESERVED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getLeaseId() { return leaseId; }
    public void setLeaseId(String leaseId) { this.leaseId = leaseId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public long getGeneration() { return generation; }
    public void setGeneration(long generation) { this.generation = generation; }
    public long getFenceToken() { return fenceToken; }
    public void setFenceToken(long fenceToken) { this.fenceToken = fenceToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public String getSessionRef() { return sessionRef; }
    public void setSessionRef(String sessionRef) { this.sessionRef = sessionRef; }
    public String getOwnedProcessRef() { return ownedProcessRef; }
    public void setOwnedProcessRef(String ownedProcessRef) { this.ownedProcessRef = ownedProcessRef; }
    public String getCleanupStatus() { return cleanupStatus; }
    public void setCleanupStatus(String cleanupStatus) { this.cleanupStatus = cleanupStatus; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
