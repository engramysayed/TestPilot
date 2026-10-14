package delivery.mobile.lease;

import java.time.Instant;

/** Public/internal lease snapshot (M1-04). */
public class MobileLeaseRecord {
    private String leaseId;
    private String deviceId;
    private String tenantId;
    private String jobId;
    private String attemptId;
    private String inputHash;
    private long generation;
    private long fenceToken;
    private Instant expiresAt;
    private Instant heartbeatAt;
    private String sessionRef;
    private String ownedProcessRef;
    private String cleanupStatus;
    private String state;
    private boolean commandsAllowed;
    private Instant createdAt;

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
    public boolean isCommandsAllowed() { return commandsAllowed; }
    public void setCommandsAllowed(boolean commandsAllowed) { this.commandsAllowed = commandsAllowed; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
