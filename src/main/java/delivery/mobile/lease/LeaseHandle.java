package delivery.mobile.lease;

import delivery.mobile.persistence.MobileDeviceLeaseEntity;

/**
 * Admission claim: durable lease row + host-scoped physical lock.
 * Closing the handle only drops the OS/JVM host lock; it does <em>not</em> release the
 * durable lease (expiry alone never frees the device — cleanup acknowledgment does).
 */
public final class LeaseHandle implements AutoCloseable {
    private final MobileLeaseRecord record;
    private final MobileDeviceLeaseEntity entity;
    private final HostDeviceLock hostLock;
    private boolean hostLockReleased;

    LeaseHandle(MobileLeaseRecord record, MobileDeviceLeaseEntity entity, HostDeviceLock hostLock) {
        this.record = record;
        this.entity = entity;
        this.hostLock = hostLock;
    }

    public MobileLeaseRecord record() {
        return record;
    }

    public MobileDeviceLeaseEntity entity() {
        return entity;
    }

    public HostDeviceLock hostLock() {
        return hostLock;
    }

    public String leaseId() {
        return record.getLeaseId();
    }

    public long fenceToken() {
        return record.getFenceToken();
    }

    /** Release only the host lock (e.g. process exit). Durable lease remains until cleanup ACK / reconcile. */
    public void releaseHostLock() {
        if (!hostLockReleased && hostLock != null) {
            hostLockReleased = true;
            hostLock.close();
        }
    }

    @Override
    public void close() {
        releaseHostLock();
    }
}
