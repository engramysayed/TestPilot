package delivery.mobile.appium;

import delivery.mobile.adb.AdbDeviceDiscovery;
import delivery.mobile.adb.AdbDeviceInfo;
import delivery.mobile.adb.AdbErrorCode;
import delivery.mobile.adb.AdbInventoryException;
import org.springframework.stereotype.Component;

/**
 * M1-06 preflight: device readiness + Appium availability. Diagnostics only —
 * no install/reset/session creation side effects.
 */
@Component
public class MobilePreflight {
    private final AdbDeviceDiscovery discovery;
    private final AppiumProcessBackend appiumBackend;

    public MobilePreflight(AdbDeviceDiscovery discovery, AppiumProcessBackend appiumBackend) {
        this.discovery = discovery;
        this.appiumBackend = appiumBackend;
    }

    /**
     * Check pinned serial + Appium harness. Outcomes:
     * {@link PreflightOutcome#DEVICE_OFFLINE}, {@link PreflightOutcome#UNAUTHORIZED},
     * {@link PreflightOutcome#APPIUM_UNAVAILABLE}, or {@link PreflightOutcome#OK}.
     */
    public MobilePreflightResult check(String serial) {
        if (serial == null || serial.isBlank()) {
            return new MobilePreflightResult(PreflightOutcome.DEVICE_OFFLINE,
                    "serial required", serial, null, false);
        }
        AdbDeviceInfo info;
        try {
            info = discovery.requireDevice(serial, false);
        } catch (AdbInventoryException e) {
            if (e.getCode() == AdbErrorCode.DEVICE_MISSING) {
                return new MobilePreflightResult(PreflightOutcome.DEVICE_OFFLINE,
                        e.getMessage(), serial, null, appiumBackend.isAvailable());
            }
            if (e.getCode() == AdbErrorCode.DEVICE_UNAUTHORIZED) {
                return new MobilePreflightResult(PreflightOutcome.UNAUTHORIZED,
                        e.getMessage(), serial, "unauthorized", appiumBackend.isAvailable());
            }
            if (e.getCode() == AdbErrorCode.DEVICE_OFFLINE) {
                return new MobilePreflightResult(PreflightOutcome.DEVICE_OFFLINE,
                        e.getMessage(), serial, "offline", appiumBackend.isAvailable());
            }
            return new MobilePreflightResult(PreflightOutcome.DEVICE_OFFLINE,
                    e.getMessage(), serial, null, appiumBackend.isAvailable());
        }

        String state = info.getAdbState() == null ? "" : info.getAdbState().toLowerCase();
        if ("unauthorized".equals(state)) {
            return new MobilePreflightResult(PreflightOutcome.UNAUTHORIZED,
                    "device unauthorized for serial " + info.getSerial(),
                    info.getSerial(), state, appiumBackend.isAvailable());
        }
        if ("offline".equals(state) || !info.isOnlineAuthorized()) {
            return new MobilePreflightResult(PreflightOutcome.DEVICE_OFFLINE,
                    "device offline/not ready for serial " + info.getSerial() + " state=" + info.getAdbState(),
                    info.getSerial(), state, appiumBackend.isAvailable());
        }

        if (!appiumBackend.isAvailable()) {
            return new MobilePreflightResult(PreflightOutcome.APPIUM_UNAVAILABLE,
                    "Appium CLI unavailable (not installed or path unset)",
                    info.getSerial(), state, false);
        }
        return MobilePreflightResult.ok(info.getSerial(), state);
    }

    /** Raise if not OK — used by supervised start. */
    public MobilePreflightResult requireOk(String serial) {
        MobilePreflightResult r = check(serial);
        if (r.isOk()) {
            return r;
        }
        AppiumErrorCode code = switch (r.getOutcome()) {
            case UNAUTHORIZED -> AppiumErrorCode.UNAUTHORIZED;
            case APPIUM_UNAVAILABLE -> AppiumErrorCode.APPIUM_UNAVAILABLE;
            case DEVICE_OFFLINE, OK -> AppiumErrorCode.DEVICE_OFFLINE;
        };
        throw new AppiumLifecycleException(code, r.getMessage());
    }
}
