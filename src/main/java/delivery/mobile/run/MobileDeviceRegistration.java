package delivery.mobile.run;

import delivery.mobile.MobilePortalStore;
import delivery.mobile.adb.AdbDeviceDiscovery;
import delivery.mobile.adb.AdbDeviceInfo;
import delivery.mobile.model.MobileDeviceRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the Android devices this Keel host can see over ADB (previously {@code registerDevice} had no caller,
 * so no device could ever be leased). Only online + authorized devices are registered; the DB keeps a
 * fingerprint, this host keeps the serial ({@link DeviceSerialResolver}).
 */
@Service
public class MobileDeviceRegistration {

    private final AdbDeviceDiscovery adb;
    private final MobilePortalStore store;
    private final DeviceSerialResolver serials;

    public MobileDeviceRegistration(AdbDeviceDiscovery adb, MobilePortalStore store, DeviceSerialResolver serials) {
        this.adb = adb;
        this.store = store;
        this.serials = serials;
    }

    public List<Map<String, Object>> discover(String tenantId) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AdbDeviceInfo info : adb.listDevices()) {
            Map<String, Object> row = new LinkedHashMap<>();
            String serial = info.getSerial();
            row.put("serialShort", shortSerial(serial));
            row.put("model", info.getModel());
            row.put("state", info.getAdbState());
            if (!info.isOnlineAuthorized()) {
                row.put("registered", false);
                row.put("reason", "unauthorized".equalsIgnoreCase(info.getAdbState())
                        ? "Unlock the phone and accept the USB debugging prompt"
                        : "Device is " + info.getAdbState());
                out.add(row);
                continue;
            }
            AdbDeviceInfo full = adb.requireDevice(serial, true);
            String fp = DeviceSerialResolver.fingerprint(serials.hostId(), serial);
            serials.remember(fp, serial);
            MobileDeviceRecord rec = store.registerDevice(tenantId, serials.hostId(), fp, shortSerial(serial),
                    full.getDisplayName(), full.getModel(), full.getApiLevel(), full.getAbi());
            row.put("registered", true);
            row.put("deviceId", rec.getDeviceId());
            out.add(row);
        }
        return out;
    }

    static String shortSerial(String serial) {
        if (serial == null || serial.length() <= 4) {
            return serial == null ? "" : serial;
        }
        return "…" + serial.substring(serial.length() - 4);
    }
}
