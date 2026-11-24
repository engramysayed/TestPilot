package delivery.mobile.run;

import delivery.mobile.MobileProperties;
import delivery.mobile.adb.AdbInputGuard;
import delivery.mobile.adb.AdbInventoryException;
import delivery.mobile.adb.AdbScreenGrab;
import delivery.mobile.adb.H264Fmp4;
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
    private final MobileProperties mobileProps;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public PhoneLiveStream(MobileDeviceRepository devices, DeviceSerialResolver serials, AdbScreenGrab grab,
                           MobileProperties mobileProps) {
        this.devices = devices;
        this.serials = serials;
        this.grab = grab;
        this.mobileProps = mobileProps == null ? new MobileProperties() : mobileProps;
    }

    /** H.264 from {@code screenrecord}, wrapped so a browser video element can play it. */
    public void writeVideo(String tenantId, String deviceId, OutputStream out) throws IOException {
        MobileDeviceEntity device = devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Phone not found"));
        if (tenantId != null && !tenantId.equals(device.getTenantId())) {
            throw new IllegalArgumentException("Phone not found");
        }
        String pause = pauseReason(device);
        if (pause != null) {
            throw new IllegalStateException(pause);
        }
        String serial = serials.resolve(device.getSerialFingerprint())
                .orElseThrow(() -> new IllegalStateException("Phone is not connected"));
        String safe;
        try {
            safe = AdbInputGuard.requireSerial(serial);
        } catch (AdbInventoryException e) {
            throw new IllegalStateException("Phone is not connected");
        }
        String bin = mobileProps.getAdbPath();
        if (bin == null || bin.isBlank()) {
            throw new IllegalStateException("adb path not configured");
        }
        Process proc = new ProcessBuilder(bin.trim(), "-s", safe, "exec-out", "screenrecord",
                "--output-format=h264", "--bit-rate", "2000000", "--size", "720x1280",
                "--time-limit", "180", "-")
                .redirectErrorStream(false)
                .start();
        Thread err = new Thread(() -> {
            try {
                proc.getErrorStream().readNBytes(8_000);
            } catch (Exception ignored) {
                // process closed
            }
        }, "screenrecord-err");
        err.setDaemon(true);
        err.start();
        try {
            H264Fmp4 mux = new H264Fmp4(720, 1280);
            byte[] buf = new byte[16_384];
            while (proc.isAlive() || proc.getInputStream().available() > 0) {
                if (pauseReason(devices.findByDeviceId(deviceId).orElse(device)) != null) {
                    return;
                }
                int n = proc.getInputStream().read(buf);
                if (n < 0) {
                    break;
                }
                byte[] mp4 = mux.push(java.util.Arrays.copyOf(buf, n));
                if (mp4.length > 0) {
                    out.write(mp4);
                    out.flush();
                }
            }
        } finally {
            proc.destroyForcibly();
        }
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

    /** Empty when a live picture is allowed. Otherwise the sentence the page should show. */
    public String note(String tenantId, String deviceId) {
        MobileDeviceEntity device = devices.findByDeviceId(deviceId).orElse(null);
        if (device == null || (tenantId != null && !tenantId.equals(device.getTenantId()))) {
            return "Phone not found";
        }
        String pause = pauseReason(device);
        if (pause != null) {
            return pause;
        }
        if (serials.resolve(device.getSerialFingerprint()).isEmpty()) {
            return "Phone is not connected";
        }
        return "";
    }

    private static String pauseReason(MobileDeviceEntity device) {
        String busy = device.getBusy();
        if (busy != null && !busy.isBlank() && !"AVAILABLE".equalsIgnoreCase(busy)) {
            return "This phone is in a run. The live picture is paused so the run stays fast.";
        }
        return null;
    }

    private byte[] capture(String tenantId, String deviceId) {
        MobileDeviceEntity device = devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Phone not found"));
        if (tenantId != null && !tenantId.equals(device.getTenantId())) {
            throw new IllegalArgumentException("Phone not found");
        }
        String pause = pauseReason(device);
        if (pause != null) {
            throw new IllegalStateException(pause);
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
