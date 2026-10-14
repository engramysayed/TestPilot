package delivery.mobile.appium;

import delivery.mobile.lease.MobileLeaseRecord;

/**
 * Narrow lease ops used by {@link SupervisedAppiumLifecycle} (ACTIVE↔RELEASING / quarantine).
 * Implemented against {@link delivery.mobile.lease.DeviceLeaseGuard} patterns without
 * pulling Appium concerns into the lease package.
 */
public interface LeaseLifecycleGate {
    MobileLeaseRecord activate(String leaseId, long fenceToken, String sessionRef, String ownedProcessRef);

    MobileLeaseRecord beginRelease(String leaseId, long fenceToken);

    void acknowledgeCleanup(String leaseId, long fenceToken);

    MobileLeaseRecord quarantine(String leaseId, long fenceToken, String reason);

    MobileLeaseRecord getLease(String leaseId);
}
