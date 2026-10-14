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
@Table(name = "mobile_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mobile_devices_device_id", columnNames = "device_id"),
                @UniqueConstraint(name = "uk_mobile_devices_host_serial", columnNames = {"host_id", "serial_fingerprint"})
        },
        indexes = @Index(name = "idx_mobile_devices_tenant", columnList = "tenant_id"))
public class MobileDeviceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "host_id", nullable = false, length = 64)
    private String hostId;

    /** Opaque fingerprint of ADB serial for unique physical binding; not the raw serial. */
    @Column(name = "serial_fingerprint", nullable = false, length = 128)
    private String serialFingerprint;

    /** Short display form for UI; full serial never echoed to MEMBER. */
    @Column(name = "serial_short", length = 32)
    private String serialShort;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(length = 128)
    private String model;

    @Column(name = "api_level")
    private Integer apiLevel;

    @Column(length = 64)
    private String abi;

    /** AUTHORIZED | UNAUTHORIZED | OFFLINE | MISSING */
    @Column(name = "authorized_state", nullable = false, length = 32)
    private String authorizedState = "MISSING";

    /** READY | BUSY | NOT_AUTHORIZED | OFFLINE | MISSING | QUARANTINED | UNKNOWN */
    @Column(nullable = false, length = 32)
    private String readiness = "UNKNOWN";

    /** AVAILABLE | RESERVED | ACTIVE | RELEASING | QUARANTINED */
    @Column(nullable = false, length = 32)
    private String busy = "AVAILABLE";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** Monotonic fencing sequence; admission increments before binding a new lease. */
    @Column(name = "fence_seq", nullable = false)
    private long fenceSeq = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getSerialFingerprint() { return serialFingerprint; }
    public void setSerialFingerprint(String serialFingerprint) { this.serialFingerprint = serialFingerprint; }
    public String getSerialShort() { return serialShort; }
    public void setSerialShort(String serialShort) { this.serialShort = serialShort; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getApiLevel() { return apiLevel; }
    public void setApiLevel(Integer apiLevel) { this.apiLevel = apiLevel; }
    public String getAbi() { return abi; }
    public void setAbi(String abi) { this.abi = abi; }
    public String getAuthorizedState() { return authorizedState; }
    public void setAuthorizedState(String authorizedState) { this.authorizedState = authorizedState; }
    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness; }
    public String getBusy() { return busy; }
    public void setBusy(String busy) { this.busy = busy; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public long getFenceSeq() { return fenceSeq; }
    public void setFenceSeq(long fenceSeq) { this.fenceSeq = fenceSeq; }
}
