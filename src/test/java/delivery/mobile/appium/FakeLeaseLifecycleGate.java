package delivery.mobile.appium;

import delivery.mobile.lease.LeaseCleanupStatus;
import delivery.mobile.lease.LeaseState;
import delivery.mobile.lease.MobileLeaseRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory lease gate for M1-06 unit tests (mirrors DeviceLeaseGuard transitions). */
public final class FakeLeaseLifecycleGate implements LeaseLifecycleGate {
    public record Event(String type, String leaseId, String detail) {}

    private final ConcurrentHashMap<String, MobileLeaseRecord> leases = new ConcurrentHashMap<>();
    private final List<Event> events = new ArrayList<>();

    public MobileLeaseRecord seedReserved(String leaseId, long fence, String deviceId) {
        MobileLeaseRecord r = new MobileLeaseRecord();
        r.setLeaseId(leaseId);
        r.setFenceToken(fence);
        r.setDeviceId(deviceId);
        r.setTenantId("tenant_t");
        r.setJobId("job_j");
        r.setAttemptId("att_a");
        r.setInputHash("hash");
        r.setState(LeaseState.RESERVED.name());
        r.setCleanupStatus(LeaseCleanupStatus.NONE.name());
        r.setCommandsAllowed(true);
        r.setCreatedAt(Instant.parse("2026-10-14T12:00:00Z"));
        leases.put(leaseId, r);
        return r;
    }

    public List<Event> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public boolean wasQuarantined(String leaseId) {
        MobileLeaseRecord r = leases.get(leaseId);
        return r != null && LeaseState.QUARANTINED.name().equals(r.getState());
    }

    private void log(String type, String leaseId, String detail) {
        synchronized (events) {
            events.add(new Event(type, leaseId, detail));
        }
    }

    @Override
    public MobileLeaseRecord activate(String leaseId, long fenceToken, String sessionRef, String ownedProcessRef) {
        MobileLeaseRecord r = require(leaseId, fenceToken);
        if (!LeaseState.RESERVED.name().equals(r.getState())) {
            throw new IllegalStateException("activate requires RESERVED");
        }
        r.setState(LeaseState.ACTIVE.name());
        r.setSessionRef(sessionRef);
        r.setOwnedProcessRef(ownedProcessRef);
        log("activate", leaseId, sessionRef + "/" + ownedProcessRef);
        return copy(r);
    }

    @Override
    public MobileLeaseRecord beginRelease(String leaseId, long fenceToken) {
        MobileLeaseRecord r = require(leaseId, fenceToken);
        r.setState(LeaseState.RELEASING.name());
        r.setCleanupStatus(LeaseCleanupStatus.PENDING.name());
        log("beginRelease", leaseId, null);
        return copy(r);
    }

    @Override
    public void acknowledgeCleanup(String leaseId, long fenceToken) {
        MobileLeaseRecord r = require(leaseId, fenceToken);
        if (!LeaseState.RELEASING.name().equals(r.getState())) {
            throw new IllegalStateException("ack requires RELEASING");
        }
        r.setCleanupStatus(LeaseCleanupStatus.ACKED.name());
        leases.remove(leaseId); // freed — no lease row == device AVAILABLE
        log("acknowledgeCleanup", leaseId, null);
    }

    @Override
    public MobileLeaseRecord quarantine(String leaseId, long fenceToken, String reason) {
        MobileLeaseRecord r = require(leaseId, fenceToken);
        r.setState(LeaseState.QUARANTINED.name());
        r.setCleanupStatus(LeaseCleanupStatus.UNCERTAIN.name());
        log("quarantine", leaseId, reason);
        return copy(r);
    }

    @Override
    public MobileLeaseRecord getLease(String leaseId) {
        MobileLeaseRecord r = leases.get(leaseId);
        if (r == null) {
            throw new IllegalStateException("lease not found");
        }
        return copy(r);
    }

    private MobileLeaseRecord require(String leaseId, long fenceToken) {
        MobileLeaseRecord r = leases.get(leaseId);
        if (r == null) {
            throw new IllegalStateException("lease not found");
        }
        if (r.getFenceToken() != fenceToken) {
            throw new IllegalStateException("fence mismatch");
        }
        return r;
    }

    private static MobileLeaseRecord copy(MobileLeaseRecord s) {
        MobileLeaseRecord r = new MobileLeaseRecord();
        r.setLeaseId(s.getLeaseId());
        r.setDeviceId(s.getDeviceId());
        r.setTenantId(s.getTenantId());
        r.setJobId(s.getJobId());
        r.setAttemptId(s.getAttemptId());
        r.setInputHash(s.getInputHash());
        r.setGeneration(s.getGeneration());
        r.setFenceToken(s.getFenceToken());
        r.setExpiresAt(s.getExpiresAt());
        r.setHeartbeatAt(s.getHeartbeatAt());
        r.setSessionRef(s.getSessionRef());
        r.setOwnedProcessRef(s.getOwnedProcessRef());
        r.setCleanupStatus(s.getCleanupStatus());
        r.setState(s.getState());
        r.setCommandsAllowed(s.isCommandsAllowed());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
