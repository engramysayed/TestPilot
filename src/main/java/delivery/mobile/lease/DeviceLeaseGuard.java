package delivery.mobile.lease;

import delivery.mobile.MobileProperties;
import delivery.mobile.model.MobileDeviceBusy;
import delivery.mobile.model.MobileDeviceReadiness;
import delivery.mobile.persistence.MobileDeviceEntity;
import delivery.mobile.persistence.MobileDeviceLeaseEntity;
import delivery.mobile.persistence.MobileDeviceLeaseRepository;
import delivery.mobile.persistence.MobileDeviceRepository;
import delivery.portal.DeliveryPortalProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1-04: host-scoped physical device lock + durable lease/fence with CAS transitions
 * and quarantine reconciliation.
 * <p>
 * States: AVAILABLE → RESERVED → ACTIVE → RELEASING → AVAILABLE; errors → QUARANTINED.
 * Admission increments a monotonic fencing token and binds device + attempt + token + input hash.
 * Heartbeat refreshes expiry; expiry stops new commands but does <strong>not</strong> release
 * the physical device until teardown cleanup is acknowledged.
 */
@Component
public class DeviceLeaseGuard {
    private final MobileDeviceRepository deviceRepository;
    private final MobileDeviceLeaseRepository leaseRepository;
    private final MobileProperties mobileProperties;
    private final DeliveryPortalProperties portalProperties;
    private final Clock clock;
    private final TransactionTemplate tx;
    /** In-process host locks held for open leases (worker JVM). */
    private final ConcurrentHashMap<String, HostDeviceLock> heldLocks = new ConcurrentHashMap<>();

    public DeviceLeaseGuard(MobileDeviceRepository deviceRepository,
                            MobileDeviceLeaseRepository leaseRepository,
                            MobileProperties mobileProperties,
                            DeliveryPortalProperties portalProperties,
                            Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.deviceRepository = deviceRepository;
        this.leaseRepository = leaseRepository;
        this.mobileProperties = mobileProperties == null ? new MobileProperties() : mobileProperties;
        this.portalProperties = portalProperties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    Path hostLockRoot() {
        String configured = mobileProperties.getHostLockRoot();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path work = portalProperties != null && portalProperties.getWorkDir() != null
                ? Path.of(portalProperties.getWorkDir())
                : Path.of("./delivery-work");
        return work.resolve("mobile-host-locks");
    }

    /**
     * Acquire host lock + durable RESERVED lease. Fails with {@code DEVICE_BUSY} on contention.
     */
    public LeaseHandle reserve(String tenantId, String deviceId, String jobId, String attemptId, String inputHash) {
        MobileDeviceEntity probe = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_NOT_FOUND, "device not found"));
        if (!Objects.equals(tenantId, probe.getTenantId())) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.TENANT_MISMATCH, "tenant foreign reference rejected");
        }
        if (!MobileDeviceBusy.AVAILABLE.name().equals(probe.getBusy())) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_BUSY, "DEVICE_BUSY");
        }
        if (leaseRepository.findByDeviceId(deviceId).isPresent()) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_BUSY, "DEVICE_BUSY");
        }

        Optional<HostDeviceLock> lockOpt = HostDeviceLock.tryAcquire(
                hostLockRoot(), probe.getHostId(), probe.getSerialFingerprint());
        if (lockOpt.isEmpty()) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_BUSY, "DEVICE_BUSY");
        }
        HostDeviceLock hostLock = lockOpt.get();
        try {
            MobileDeviceLeaseEntity lease = tx.execute(status ->
                    durableReserve(tenantId, deviceId, jobId, attemptId, inputHash));
            MobileLeaseRecord record = toRecord(lease);
            heldLocks.put(lease.getLeaseId(), hostLock);
            return new LeaseHandle(record, lease, hostLock);
        } catch (RuntimeException e) {
            hostLock.close();
            throw e;
        }
    }

    private MobileDeviceLeaseEntity durableReserve(String tenantId, String deviceId,
                                                   String jobId, String attemptId, String inputHash) {
        MobileDeviceEntity device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_NOT_FOUND, "device not found"));
        if (!Objects.equals(tenantId, device.getTenantId())) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.TENANT_MISMATCH, "tenant foreign reference rejected");
        }
        if (!MobileDeviceBusy.AVAILABLE.name().equals(device.getBusy())) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_BUSY, "DEVICE_BUSY");
        }
        if (leaseRepository.findByDeviceId(deviceId).isPresent()) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_BUSY, "DEVICE_BUSY");
        }

        long nextFence = device.getFenceSeq() + 1L;
        device.setFenceSeq(nextFence);
        device.setBusy(MobileDeviceBusy.RESERVED.name());
        device.setReadiness(MobileDeviceReadiness.BUSY.name());
        deviceRepository.save(device);

        Instant now = clock.instant();
        MobileDeviceLeaseEntity lease = new MobileDeviceLeaseEntity();
        lease.setLeaseId("lease_" + shortId());
        lease.setDeviceId(deviceId);
        lease.setTenantId(tenantId);
        lease.setJobId(jobId);
        lease.setAttemptId(attemptId);
        lease.setInputHash(inputHash);
        lease.setGeneration(1L);
        lease.setFenceToken(nextFence);
        lease.setExpiresAt(now.plusSeconds(mobileProperties.getLeaseExpirySeconds()));
        lease.setHeartbeatAt(now);
        lease.setCleanupStatus(LeaseCleanupStatus.NONE.name());
        lease.setState(LeaseState.RESERVED.name());
        lease.setCreatedAt(now);
        try {
            return leaseRepository.saveAndFlush(lease);
        } catch (DataIntegrityViolationException e) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_BUSY, "DEVICE_BUSY");
        }
    }

    /** RESERVED → ACTIVE; binds session + owned process refs under the fence. */
    public MobileLeaseRecord activate(String leaseId, long fenceToken, String sessionRef, String ownedProcessRef) {
        return tx.execute(status -> {
            MobileDeviceLeaseEntity lease = requireLease(leaseId);
            assertFence(lease, fenceToken);
            assertState(lease, LeaseState.RESERVED);
            if (!allowsCommands(lease)) {
                throw new DeviceLeaseException(DeviceLeaseErrorCode.EXPIRED_COMMANDS,
                        "lease expired; commands stopped (device not released)");
            }
            lease.setState(LeaseState.ACTIVE.name());
            lease.setSessionRef(sessionRef);
            lease.setOwnedProcessRef(ownedProcessRef);
            Instant now = clock.instant();
            lease.setHeartbeatAt(now);
            lease.setExpiresAt(now.plusSeconds(mobileProperties.getLeaseExpirySeconds()));
            leaseRepository.save(lease);

            MobileDeviceEntity device = requireDevice(lease.getDeviceId());
            casDeviceBusy(device, MobileDeviceBusy.RESERVED, MobileDeviceBusy.ACTIVE);
            deviceRepository.save(device);
            return toRecord(lease);
        });
    }

    /**
     * Refresh expiry when fence matches and lease still allows heartbeats.
     * A late heartbeat after expiry is rejected and does not extend or release the lease.
     */
    public MobileLeaseRecord heartbeat(String leaseId, long fenceToken) {
        return tx.execute(status -> {
            MobileDeviceLeaseEntity lease = requireLease(leaseId);
            assertFence(lease, fenceToken);
            String state = lease.getState();
            if (!(LeaseState.RESERVED.name().equals(state) || LeaseState.ACTIVE.name().equals(state))) {
                throw new DeviceLeaseException(DeviceLeaseErrorCode.INVALID_STATE,
                        "heartbeat not allowed in state " + state);
            }
            Instant now = clock.instant();
            if (lease.getExpiresAt() != null && !now.isBefore(lease.getExpiresAt())) {
                throw new DeviceLeaseException(DeviceLeaseErrorCode.EXPIRED_COMMANDS,
                        "late heartbeat rejected; lease expired (device not released)");
            }
            lease.setHeartbeatAt(now);
            lease.setExpiresAt(now.plusSeconds(mobileProperties.getLeaseExpirySeconds()));
            leaseRepository.save(lease);
            return toRecord(lease);
        });
    }

    public boolean allowsCommands(String leaseId, long fenceToken) {
        MobileDeviceLeaseEntity lease = requireLease(leaseId);
        if (lease.getFenceToken() != fenceToken) {
            return false;
        }
        return allowsCommands(lease);
    }

    boolean allowsCommands(MobileDeviceLeaseEntity lease) {
        String state = lease.getState();
        if (!(LeaseState.RESERVED.name().equals(state) || LeaseState.ACTIVE.name().equals(state))) {
            return false;
        }
        Instant exp = lease.getExpiresAt();
        Instant now = clock.instant();
        return exp == null || now.isBefore(exp);
    }

    /** ACTIVE/RESERVED → RELEASING with cleanup PENDING. Does not free the device. */
    public MobileLeaseRecord beginRelease(String leaseId, long fenceToken) {
        return tx.execute(status -> {
            MobileDeviceLeaseEntity lease = requireLease(leaseId);
            assertFence(lease, fenceToken);
            String state = lease.getState();
            if (!(LeaseState.ACTIVE.name().equals(state) || LeaseState.RESERVED.name().equals(state))) {
                throw new DeviceLeaseException(DeviceLeaseErrorCode.INVALID_STATE,
                        "beginRelease not allowed in state " + state);
            }
            lease.setState(LeaseState.RELEASING.name());
            lease.setCleanupStatus(LeaseCleanupStatus.PENDING.name());
            leaseRepository.save(lease);

            MobileDeviceEntity device = requireDevice(lease.getDeviceId());
            device.setBusy(MobileDeviceBusy.RELEASING.name());
            device.setReadiness(MobileDeviceReadiness.BUSY.name());
            deviceRepository.save(device);
            return toRecord(lease);
        });
    }

    /**
     * Only path that returns the device to AVAILABLE after activation/releasing.
     * Requires cleanup acknowledgment. Rejects release without ACK.
     */
    public void acknowledgeCleanup(String leaseId, long fenceToken) {
        tx.executeWithoutResult(status -> {
            MobileDeviceLeaseEntity lease = requireLease(leaseId);
            assertFence(lease, fenceToken);
            if (!LeaseState.RELEASING.name().equals(lease.getState())) {
                throw new DeviceLeaseException(DeviceLeaseErrorCode.INVALID_STATE,
                        "cleanup acknowledgment requires RELEASING state");
            }
            if (!LeaseCleanupStatus.PENDING.name().equals(lease.getCleanupStatus())
                    && !LeaseCleanupStatus.UNCERTAIN.name().equals(lease.getCleanupStatus())) {
                throw new DeviceLeaseException(DeviceLeaseErrorCode.CLEANUP_REQUIRED,
                        "no release without cleanup acknowledgment");
            }
            freeDeviceAfterCleanup(lease, LeaseCleanupStatus.ACKED);
        });
    }

    /** Explicit rejection helper for callers that attempt TTL-only / ACK-less release. */
    public void releaseWithoutCleanupRejected(String leaseId, long fenceToken) {
        MobileDeviceLeaseEntity lease = requireLease(leaseId);
        assertFence(lease, fenceToken);
        throw new DeviceLeaseException(DeviceLeaseErrorCode.CLEANUP_REQUIRED,
                "no release without cleanup acknowledgment");
    }

    public MobileLeaseRecord quarantine(String leaseId, long fenceToken, String reason) {
        return tx.execute(status -> {
            MobileDeviceLeaseEntity lease = requireLease(leaseId);
            assertFence(lease, fenceToken);
            lease.setState(LeaseState.QUARANTINED.name());
            lease.setCleanupStatus(LeaseCleanupStatus.UNCERTAIN.name());
            leaseRepository.save(lease);

            MobileDeviceEntity device = requireDevice(lease.getDeviceId());
            device.setBusy(MobileDeviceBusy.QUARANTINED.name());
            device.setReadiness(MobileDeviceReadiness.QUARANTINED.name());
            deviceRepository.save(device);
            return toRecord(lease);
        });
    }

    /**
     * Quarantine reconciliation / abandoned RESERVED recovery.
     * <ul>
     *   <li>RESERVED with no session and expired — may free (abandoned admission before activation).</li>
     *   <li>Expired ACTIVE with session — quarantine (do not free on TTL).</li>
     *   <li>QUARANTINED / RELEASING — free only when caller proves old session/process cannot act.</li>
     * </ul>
     */
    public MobileLeaseRecord reconcile(String leaseId, QuarantineProof proof) {
        return tx.execute(status -> {
            MobileDeviceLeaseEntity lease = requireLease(leaseId);
            Instant now = clock.instant();
            boolean expired = lease.getExpiresAt() != null && !now.isBefore(lease.getExpiresAt());
            String state = lease.getState();

            if (LeaseState.RESERVED.name().equals(state)
                    && (lease.getSessionRef() == null || lease.getSessionRef().isBlank())
                    && expired) {
                freeDeviceAfterCleanup(lease, LeaseCleanupStatus.ACKED);
                return null;
            }

            if (LeaseState.ACTIVE.name().equals(state) && expired) {
                lease.setState(LeaseState.QUARANTINED.name());
                lease.setCleanupStatus(LeaseCleanupStatus.UNCERTAIN.name());
                leaseRepository.save(lease);
                MobileDeviceEntity device = requireDevice(lease.getDeviceId());
                device.setBusy(MobileDeviceBusy.QUARANTINED.name());
                device.setReadiness(MobileDeviceReadiness.QUARANTINED.name());
                deviceRepository.save(device);
                return toRecord(lease);
            }

            if (LeaseState.QUARANTINED.name().equals(state) || LeaseState.RELEASING.name().equals(state)) {
                if (proof == null || !proof.sessionCannotAct() || !proof.processCannotAct()) {
                    throw new DeviceLeaseException(DeviceLeaseErrorCode.QUARANTINE_PROOF_REQUIRED,
                            "quarantine reconciliation requires proof old session/process cannot act");
                }
                freeDeviceAfterCleanup(lease, LeaseCleanupStatus.ACKED);
                return null;
            }

            throw new DeviceLeaseException(DeviceLeaseErrorCode.INVALID_STATE,
                    "reconcile not applicable for state " + state);
        });
    }

    public MobileLeaseRecord getLease(String leaseId) {
        return toRecord(requireLease(leaseId));
    }

    public Optional<MobileLeaseRecord> findByDeviceId(String deviceId) {
        return leaseRepository.findByDeviceId(deviceId).map(this::toRecord);
    }

    public void dropHostLock(String leaseId) {
        HostDeviceLock lock = heldLocks.remove(leaseId);
        if (lock != null) {
            lock.close();
        }
    }

    public Map<String, HostDeviceLock> heldLocksView() {
        return Map.copyOf(heldLocks);
    }

    private void freeDeviceAfterCleanup(MobileDeviceLeaseEntity lease, LeaseCleanupStatus cleanup) {
        String deviceId = lease.getDeviceId();
        String leaseId = lease.getLeaseId();
        lease.setCleanupStatus(cleanup.name());
        leaseRepository.delete(lease);
        leaseRepository.flush();

        MobileDeviceEntity device = requireDevice(deviceId);
        device.setBusy(MobileDeviceBusy.AVAILABLE.name());
        if (MobileDeviceReadiness.QUARANTINED.name().equals(device.getReadiness())
                || MobileDeviceReadiness.BUSY.name().equals(device.getReadiness())) {
            device.setReadiness(MobileDeviceReadiness.READY.name());
        }
        deviceRepository.save(device);
        dropHostLock(leaseId);
    }

    private MobileDeviceLeaseEntity requireLease(String leaseId) {
        return leaseRepository.findByLeaseId(leaseId)
                .orElseThrow(() -> new DeviceLeaseException(DeviceLeaseErrorCode.LEASE_NOT_FOUND, "lease not found"));
    }

    private MobileDeviceEntity requireDevice(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DeviceLeaseException(DeviceLeaseErrorCode.DEVICE_NOT_FOUND, "device not found"));
    }

    private static void assertFence(MobileDeviceLeaseEntity lease, long fenceToken) {
        if (lease.getFenceToken() != fenceToken) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.FENCE_MISMATCH, "fence token mismatch");
        }
    }

    private static void assertState(MobileDeviceLeaseEntity lease, LeaseState expected) {
        if (!expected.name().equals(lease.getState())) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.CAS_CONFLICT,
                    "CAS conflict: expected " + expected + " was " + lease.getState());
        }
    }

    private static void casDeviceBusy(MobileDeviceEntity device, MobileDeviceBusy expected, MobileDeviceBusy next) {
        if (!expected.name().equals(device.getBusy())) {
            throw new DeviceLeaseException(DeviceLeaseErrorCode.CAS_CONFLICT,
                    "CAS conflict: device busy expected " + expected + " was " + device.getBusy());
        }
        device.setBusy(next.name());
    }

    MobileLeaseRecord toRecord(MobileDeviceLeaseEntity e) {
        MobileLeaseRecord r = new MobileLeaseRecord();
        r.setLeaseId(e.getLeaseId());
        r.setDeviceId(e.getDeviceId());
        r.setTenantId(e.getTenantId());
        r.setJobId(e.getJobId());
        r.setAttemptId(e.getAttemptId());
        r.setInputHash(e.getInputHash());
        r.setGeneration(e.getGeneration());
        r.setFenceToken(e.getFenceToken());
        r.setExpiresAt(e.getExpiresAt());
        r.setHeartbeatAt(e.getHeartbeatAt());
        r.setSessionRef(e.getSessionRef());
        r.setOwnedProcessRef(e.getOwnedProcessRef());
        r.setCleanupStatus(e.getCleanupStatus());
        r.setState(e.getState());
        r.setCommandsAllowed(allowsCommands(e));
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Proof that an old Appium session / owned process can no longer act. */
    public record QuarantineProof(boolean sessionCannotAct, boolean processCannotAct) {
        public static QuarantineProof cleared() {
            return new QuarantineProof(true, true);
        }
    }
}
