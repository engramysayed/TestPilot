package delivery.mobile.appium;

import delivery.mobile.lease.DeviceLeaseGuard;
import delivery.mobile.lease.MobileLeaseRecord;
import org.springframework.stereotype.Component;

/**
 * Adapts {@link DeviceLeaseGuard} for Appium lifecycle (crash / cleanup failure → quarantine).
 */
@Component
public class DeviceLeaseGuardLifecycleGate implements LeaseLifecycleGate {
    private final DeviceLeaseGuard guard;

    public DeviceLeaseGuardLifecycleGate(DeviceLeaseGuard guard) {
        this.guard = guard;
    }

    @Override
    public MobileLeaseRecord activate(String leaseId, long fenceToken, String sessionRef, String ownedProcessRef) {
        return guard.activate(leaseId, fenceToken, sessionRef, ownedProcessRef);
    }

    @Override
    public MobileLeaseRecord beginRelease(String leaseId, long fenceToken) {
        return guard.beginRelease(leaseId, fenceToken);
    }

    @Override
    public void acknowledgeCleanup(String leaseId, long fenceToken) {
        guard.acknowledgeCleanup(leaseId, fenceToken);
    }

    @Override
    public MobileLeaseRecord quarantine(String leaseId, long fenceToken, String reason) {
        return guard.quarantine(leaseId, fenceToken, reason);
    }

    @Override
    public MobileLeaseRecord getLease(String leaseId) {
        return guard.getLease(leaseId);
    }
}
