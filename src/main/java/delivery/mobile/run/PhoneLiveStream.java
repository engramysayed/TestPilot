package delivery.mobile.run;

import delivery.mobile.adb.AdbInventoryException;
import delivery.mobile.adb.AdbScreenGrab;
import delivery.mobile.persistence.MobileDeviceEntity;
import delivery.mobile.persistence.MobileDeviceRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Serves the phone's current screen to a browser. One adb grab at a time per phone. */
@Service
public class PhoneLiveStream {

    private final MobileDeviceRepository devices;
    private final DeviceSerialResolver serials;
    private final AdbScreenGrab grab;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public PhoneLiveStream(MobileDeviceRepository devices, DeviceSerialResolver serials, AdbScreenGrab grab) {
        this.devices = devices;
        this.serials = serials;
        this.grab = grab;
    }

    public byte[] one(String tenantId, String deviceId) {
        return capture(tenantId, deviceId);
    }

    /** Writes multipart frames for about three minutes, then the browser reconnects. */
    public void writeMjpeg(String tenantId, String deviceId, OutputStream out) throws IOException {
        long end = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        while (System.nanoTime() < end) {
            byte[] png;
            try {
                png = capture(tenantId, deviceId);
            } catch (RuntimeException e) {
                return;
            }
            writeFrame(out, png);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void writeFrame(OutputStream out, byte[] png) throws IOException {
        String head = "--keelframe\r\nContent-Type: image/png\r\nContent-Length: " + png.length + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(png);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private byte[] capture(String tenantId, String deviceId) {
        MobileDeviceEntity device = devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Phone not found"));
        if (tenantId != null && !tenantId.equals(device.getTenantId())) {
            throw new IllegalArgumentException("Phone not found");
        }
        String serial = serials.resolve(device.getSerialFingerprint())
                .orElseThrow(() -> new IllegalStateException("Phone is not connected"));
        Object lock = locks.computeIfAbsent(deviceId, k -> new Object());
        synchronized (lock) {
            try {
                return grab.capture(serial);
            } catch (AdbInventoryException e) {
                throw new IllegalStateException("Could not read the phone screen");
            }
        }
    }
}
