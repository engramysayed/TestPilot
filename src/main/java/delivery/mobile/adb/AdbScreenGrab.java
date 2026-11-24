package delivery.mobile.adb;

import delivery.mobile.MobileProperties;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One PNG of the device screen via {@code adb exec-out screencap -p}.
 * shortcut: JPEG polling, not an scrcpy H.264 socket. Upgrade when a browser scrcpy client is worth shipping.
 */
public final class AdbScreenGrab {

    private final MobileProperties props;

    public AdbScreenGrab(MobileProperties props) {
        this.props = props == null ? new MobileProperties() : props;
    }

    public byte[] capture(String serial) throws AdbInventoryException {
        String safe = AdbInputGuard.requireSerial(serial);
        String bin = props.getAdbPath();
        if (bin == null || bin.isBlank()) {
            throw new AdbInventoryException(AdbErrorCode.SDK_UNAVAILABLE, "adb path not configured");
        }
        List<String> cmd = List.of(bin.trim(), "-s", safe, "exec-out", "screencap", "-p");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        try {
            Process proc = pb.start();
            byte[] png = readAll(proc.getInputStream(), 8_000_000);
            drain(proc.getErrorStream());
            boolean finished = proc.waitFor(Math.max(5, props.getAdbTimeoutSeconds()), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "screencap timed out");
            }
            if (proc.exitValue() != 0) {
                throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "screencap failed");
            }
            return requirePng(png);
        } catch (AdbInventoryException e) {
            throw e;
        } catch (Exception e) {
            throw new AdbInventoryException(AdbErrorCode.SDK_UNAVAILABLE, "screencap failed to start");
        }
    }

    /** Rejects anything that is not a PNG, so a text error from adb is never shown as a picture. */
    public static byte[] requirePng(byte[] raw) throws AdbInventoryException {
        if (raw == null || raw.length < 8
                || (raw[0] & 0xFF) != 0x89
                || raw[1] != 'P' || raw[2] != 'N' || raw[3] != 'G') {
            throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "screencap did not return a PNG");
        }
        return raw;
    }

    private static byte[] readAll(InputStream in, int cap) throws Exception {
        byte[] buf = in.readNBytes(cap + 1);
        if (buf.length > cap) {
            throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "screencap too large");
        }
        return buf;
    }

    private static void drain(InputStream err) {
        Thread t = new Thread(() -> {
            try {
                err.readNBytes(4_000);
            } catch (Exception ignored) {
                // process closed
            }
        }, "screencap-err");
        t.setDaemon(true);
        t.start();
    }
}
