package delivery.mobile.adb;

import delivery.mobile.MobileProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M1-03: ADB inventory ({@code adb devices -l}) and installed-package metadata.
 * Always requires an exact serial for device-scoped actions; never falls back to the first device.
 * Friendly {@link AdbDeviceInfo#getDisplayName()} is display-only.
 */
@Component
public class AdbDeviceDiscovery {
    private final MobileProperties props;
    private final AdbCommandBackend backend;

    public AdbDeviceDiscovery(MobileProperties props, AdbCommandBackend backend) {
        this.props = props == null ? new MobileProperties() : props;
        this.backend = backend;
    }

    /** Tripwire: inventory never silently targets another connected device. */
    public boolean allowsFirstDeviceFallback() {
        return false;
    }

    /**
     * Parse {@code adb devices -l}. Unauthorized and offline rows are included.
     * Does not enrich with getprop (use {@link #requireDevice(String, boolean)}).
     */
    public List<AdbDeviceInfo> listDevices() throws AdbInventoryException {
        String out = backend.execute(List.of("devices", "-l"));
        return AdbDevicesParser.parse(out);
    }

    /**
     * Resolve the exact serial from the current inventory. Never returns another device
     * when the serial is absent.
     */
    public AdbDeviceInfo requireDevice(String serial) throws AdbInventoryException {
        return requireDevice(serial, false);
    }

    /**
     * @param enrich when true and the device is online/authorized, query model/API/ABI via getprop
     */
    public AdbDeviceInfo requireDevice(String serial, boolean enrich) throws AdbInventoryException {
        String exact = AdbInputGuard.requireSerial(serial);
        List<AdbDeviceInfo> all = listDevices();
        AdbDeviceInfo match = null;
        for (AdbDeviceInfo d : all) {
            if (exact.equals(d.getSerial())) {
                match = d;
                break;
            }
        }
        if (match == null) {
            throw new AdbInventoryException(AdbErrorCode.DEVICE_MISSING,
                    "ADB serial not present in inventory: " + exact
                            + " (connected=" + all.size() + "; no first-device fallback)");
        }
        if (enrich && match.isOnlineAuthorized()) {
            return enrich(match);
        }
        return match;
    }

    /**
     * Fail if the selected serial is missing, unauthorized, or offline before any app command.
     * Still never redirects to another connected device.
     */
    public AdbDeviceInfo requireOnlineAuthorized(String serial) throws AdbInventoryException {
        AdbDeviceInfo info = requireDevice(serial, true);
        String state = info.getAdbState() == null ? "" : info.getAdbState().toLowerCase(Locale.ROOT);
        if ("unauthorized".equals(state)) {
            throw new AdbInventoryException(AdbErrorCode.DEVICE_UNAUTHORIZED,
                    "device unauthorized for serial " + info.getSerial());
        }
        if ("offline".equals(state)) {
            throw new AdbInventoryException(AdbErrorCode.DEVICE_OFFLINE,
                    "device offline for serial " + info.getSerial());
        }
        if (!info.isOnlineAuthorized()) {
            throw new AdbInventoryException(AdbErrorCode.DEVICE_OFFLINE,
                    "device not ready (state=" + info.getAdbState() + ") for serial " + info.getSerial());
        }
        return info;
    }

    /**
     * Observe package identity on the selected serial only ({@code verification=OBSERVED}).
     */
    public InstalledPackageMetadata resolveInstalledPackage(String serial, String packageName)
            throws AdbInventoryException {
        String exactSerial = AdbInputGuard.requireSerial(serial);
        String pkg = AdbInputGuard.requirePackageName(packageName);
        requireOnlineAuthorized(exactSerial);

        String pmPath = backend.execute(List.of("-s", exactSerial, "shell", "pm", "path", pkg));
        if (!InstalledPackageParser.pmPathIndicatesInstalled(pmPath)) {
            throw new AdbInventoryException(AdbErrorCode.PACKAGE_NOT_FOUND,
                    "package not installed on serial " + exactSerial + ": " + pkg);
        }

        Long versionCode = null;
        String versionName = null;
        try {
            String dumpsys = backend.execute(List.of("-s", exactSerial, "shell", "dumpsys", "package", pkg));
            versionCode = InstalledPackageParser.parseVersionCode(dumpsys);
            versionName = InstalledPackageParser.parseVersionName(dumpsys);
        } catch (AdbInventoryException e) {
            // Package exists (pm path succeeded); version fields remain optional if dumpsys fails.
            if (e.getCode() == AdbErrorCode.SDK_UNAVAILABLE) {
                throw e;
            }
        }
        return new InstalledPackageMetadata(exactSerial, pkg, versionCode, versionName);
    }

    private AdbDeviceInfo enrich(AdbDeviceInfo base) {
        String serial = base.getSerial();
        Integer api = null;
        String abi = null;
        String model = base.getModel();
        try {
            String modelProp = shellGetprop(serial, "ro.product.model");
            if (modelProp != null && !modelProp.isBlank()) {
                model = modelProp.trim();
            }
        } catch (AdbInventoryException ignored) {
            // enrichment is best-effort when device is listed as online
        }
        try {
            String sdk = shellGetprop(serial, "ro.build.version.sdk");
            if (sdk != null && !sdk.isBlank()) {
                api = Integer.parseInt(sdk.trim());
            }
        } catch (Exception ignored) {
            // leave null
        }
        try {
            String abiProp = shellGetprop(serial, "ro.product.cpu.abi");
            if (abiProp != null && !abiProp.isBlank()) {
                abi = abiProp.trim();
            }
        } catch (AdbInventoryException ignored) {
            // leave null
        }
        String display = firstNonBlank(model, base.getDisplayName(), base.getProduct(), serial);
        return base.withEnrichment(api, abi, model, display);
    }

    private String shellGetprop(String serial, String prop) throws AdbInventoryException {
        // prop names are fixed literals from callers — still reject oddities
        if (prop == null || prop.isBlank() || prop.indexOf(' ') >= 0 || prop.startsWith("-")) {
            throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "invalid getprop key");
        }
        return backend.execute(List.of("-s", serial, "shell", "getprop", prop));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** Expose configured adb path for diagnostics (never a secret). */
    public String configuredAdbPath() {
        return props.getAdbPath();
    }

    /** Test helper: parse without invoking adb. */
    static List<AdbDeviceInfo> parseDevicesOutput(String raw) {
        return AdbDevicesParser.parse(raw);
    }

    /** Collect serials currently listed (order preserved). */
    public List<String> listSerials() throws AdbInventoryException {
        List<String> serials = new ArrayList<>();
        for (AdbDeviceInfo d : listDevices()) {
            serials.add(d.getSerial());
        }
        return serials;
    }
}
