package delivery.mobile.adb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code adb devices -l} output. Does not invent devices or pick a default serial.
 */
public final class AdbDevicesParser {
    private AdbDevicesParser() {
    }

    public static List<AdbDeviceInfo> parse(String raw) {
        List<AdbDeviceInfo> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("list of devices") || lower.startsWith("* daemon")) {
                continue;
            }
            AdbDeviceInfo info = parseLine(trimmed);
            if (info != null) {
                out.add(info);
            }
        }
        return out;
    }

    static AdbDeviceInfo parseLine(String line) {
        // SERIAL<whitespace>STATE[ key:value ...]
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        String serial = parts[0];
        String state = parts[1];
        if (serial.isEmpty() || state.isEmpty()) {
            return null;
        }
        Map<String, String> kv = new LinkedHashMap<>();
        for (int i = 2; i < parts.length; i++) {
            String token = parts[i];
            int colon = token.indexOf(':');
            if (colon > 0 && colon < token.length() - 1) {
                kv.put(token.substring(0, colon), token.substring(colon + 1));
            }
        }
        String model = kv.get("model");
        String product = kv.get("product");
        String device = kv.get("device");
        String usb = kv.get("usb");
        String transportId = kv.get("transport_id");
        String display = firstNonBlank(model, product, device, serial);
        return new AdbDeviceInfo(serial, state, product, model, device, usb, transportId,
                null, null, display);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
