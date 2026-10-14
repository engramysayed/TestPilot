package delivery.mobile.appium;

import java.util.function.BooleanSupplier;

/**
 * Start request for a supervised Appium CLI process (argument-array only; no Appium jars).
 */
public final class AppiumStartRequest {
    private final String ownerKey;
    private final String leaseId;
    private final long fenceToken;
    private final String deviceSerial;
    private final OwnedLoopbackPortAllocator.PortBundle ports;
    private final BooleanSupplier cancel;

    public AppiumStartRequest(String ownerKey,
                              String leaseId,
                              long fenceToken,
                              String deviceSerial,
                              OwnedLoopbackPortAllocator.PortBundle ports,
                              BooleanSupplier cancel) {
        this.ownerKey = ownerKey;
        this.leaseId = leaseId;
        this.fenceToken = fenceToken;
        this.deviceSerial = deviceSerial;
        this.ports = ports;
        this.cancel = cancel == null ? () -> false : cancel;
    }

    public String getOwnerKey() { return ownerKey; }
    public String getLeaseId() { return leaseId; }
    public long getFenceToken() { return fenceToken; }
    public String getDeviceSerial() { return deviceSerial; }
    public OwnedLoopbackPortAllocator.PortBundle getPorts() { return ports; }
    public BooleanSupplier getCancel() { return cancel; }

    public boolean isCancelled() {
        try {
            return cancel.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
