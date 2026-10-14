package delivery.mobile.adb;

import delivery.job.ProcessSupervisor;
import delivery.mobile.MobileProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Argument-array {@code adb} execution via {@link ProcessSupervisor}. Never uses a shell.
 */
public final class ProcessAdbCommandBackend implements AdbCommandBackend {
    private final MobileProperties props;

    public ProcessAdbCommandBackend(MobileProperties props) {
        this.props = props == null ? new MobileProperties() : props;
    }

    @Override
    public String execute(List<String> args) throws AdbInventoryException {
        String bin = props.getAdbPath();
        if (bin == null || bin.isBlank()) {
            throw new AdbInventoryException(AdbErrorCode.SDK_UNAVAILABLE, "adb path not configured");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(bin.trim());
        if (args != null) {
            cmd.addAll(args);
        }
        Duration deadline = Duration.ofSeconds(Math.max(5, props.getAdbTimeoutSeconds()));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            ProcessSupervisor.Result result = ProcessSupervisor.run(pb, deadline, () -> false, 256_000);
            if (result.timedOut()) {
                throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "adb timed out");
            }
            if (result.cancelled()) {
                throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "adb cancelled");
            }
            // adb devices returns 0 even with empty list; other commands may be non-zero
            if (result.exitCode() != 0) {
                throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED,
                        "adb exit=" + result.exitCode() + " " + safe(result.output()));
            }
            return result.output() == null ? "" : result.output();
        } catch (AdbInventoryException e) {
            throw e;
        } catch (Exception e) {
            throw new AdbInventoryException(AdbErrorCode.SDK_UNAVAILABLE,
                    "adb failed to start: " + e.getMessage());
        }
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
