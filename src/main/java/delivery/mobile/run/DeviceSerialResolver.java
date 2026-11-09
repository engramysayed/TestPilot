package delivery.mobile.run;

import delivery.mobile.adb.AdbDeviceDiscovery;
import delivery.mobile.adb.AdbDeviceInfo;
import delivery.portal.DeliveryPortalProperties;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The database stores only a serial fingerprint (privacy). Appium needs the real ADB serial, so this host keeps
 * a local fingerprint → serial map ({@code delivery-store/mobile/host-serials.json}) and falls back to a live
 * {@code adb devices} scan, matching by fingerprint.
 */
@Component
public class DeviceSerialResolver {

    private final AdbDeviceDiscovery adb;
    private final Path mapFile;
    private final String hostId;

    public DeviceSerialResolver(AdbDeviceDiscovery adb, DeliveryPortalProperties props) {
        this.adb = adb;
        this.mapFile = Path.of(props.getStoreRoot()).resolve("mobile").resolve("host-serials.json");
        this.hostId = resolveHostId();
    }

    public String hostId() {
        return hostId;
    }

    public static String fingerprint(String hostId, String serial) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((hostId + ":" + serial).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void remember(String fingerprint, String serial) {
        try {
            Files.createDirectories(mapFile.getParent());
            JSONObject o = Files.isRegularFile(mapFile)
                    ? new JSONObject(Files.readString(mapFile, StandardCharsets.UTF_8)) : new JSONObject();
            o.put(fingerprint, serial);
            Files.writeString(mapFile, o.toString(2), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // the live adb scan still resolves it
        }
    }

    public synchronized Optional<String> resolve(String fingerprint) {
        try {
            if (Files.isRegularFile(mapFile)) {
                JSONObject o = new JSONObject(Files.readString(mapFile, StandardCharsets.UTF_8));
                String s = o.optString(fingerprint, "");
                if (!s.isBlank()) {
                    return Optional.of(s);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            for (AdbDeviceInfo d : adb.listDevices()) {
                if (fingerprint.equals(fingerprint(hostId, d.getSerial()))) {
                    remember(fingerprint, d.getSerial());
                    return Optional.of(d.getSerial());
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static String resolveHostId() {
        String env = System.getenv("KEEL_MOBILE_HOST_ID");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "local";
        }
    }
}
