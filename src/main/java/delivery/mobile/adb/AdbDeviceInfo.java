package delivery.mobile.adb;

import delivery.mobile.model.MobileAuthorizedState;
import delivery.mobile.model.MobileDeviceReadiness;

/**
 * One row from {@code adb devices -l}, optionally enriched with getprop model/API/ABI.
 * {@link #displayName} is friendly / Unicode-capable and <strong>display-only</strong>
 * — never used to select a device.
 */
public final class AdbDeviceInfo {
    private final String serial;
    private final String adbState;
    private final String product;
    private final String model;
    private final String device;
    private final String usb;
    private final String transportId;
    private final Integer apiLevel;
    private final String abi;
    private final String displayName;

    public AdbDeviceInfo(String serial,
                         String adbState,
                         String product,
                         String model,
                         String device,
                         String usb,
                         String transportId,
                         Integer apiLevel,
                         String abi,
                         String displayName) {
        this.serial = serial;
        this.adbState = adbState;
        this.product = product;
        this.model = model;
        this.device = device;
        this.usb = usb;
        this.transportId = transportId;
        this.apiLevel = apiLevel;
        this.abi = abi;
        this.displayName = displayName;
    }

    public String getSerial() { return serial; }
    public String getAdbState() { return adbState; }
    public String getProduct() { return product; }
    public String getModel() { return model; }
    public String getDevice() { return device; }
    public String getUsb() { return usb; }
    public String getTransportId() { return transportId; }
    public Integer getApiLevel() { return apiLevel; }
    public String getAbi() { return abi; }
    /** Friendly name for UI only; may contain Unicode. Not a selection key. */
    public String getDisplayName() { return displayName; }

    public boolean isOnlineAuthorized() {
        return "device".equalsIgnoreCase(adbState);
    }

    public MobileAuthorizedState toAuthorizedState() {
        if (adbState == null) {
            return MobileAuthorizedState.MISSING;
        }
        return switch (adbState.toLowerCase()) {
            case "device" -> MobileAuthorizedState.AUTHORIZED;
            case "unauthorized" -> MobileAuthorizedState.UNAUTHORIZED;
            case "offline" -> MobileAuthorizedState.OFFLINE;
            default -> MobileAuthorizedState.OFFLINE;
        };
    }

    public MobileDeviceReadiness toReadiness() {
        if (adbState == null) {
            return MobileDeviceReadiness.MISSING;
        }
        return switch (adbState.toLowerCase()) {
            case "device" -> MobileDeviceReadiness.READY;
            case "unauthorized" -> MobileDeviceReadiness.NOT_AUTHORIZED;
            case "offline" -> MobileDeviceReadiness.OFFLINE;
            default -> MobileDeviceReadiness.UNKNOWN;
        };
    }

    public AdbDeviceInfo withEnrichment(Integer apiLevel, String abi, String modelOverride, String displayName) {
        return new AdbDeviceInfo(
                serial,
                adbState,
                product,
                modelOverride != null && !modelOverride.isBlank() ? modelOverride : model,
                device,
                usb,
                transportId,
                apiLevel != null ? apiLevel : this.apiLevel,
                abi != null && !abi.isBlank() ? abi : this.abi,
                displayName != null ? displayName : this.displayName);
    }
}
