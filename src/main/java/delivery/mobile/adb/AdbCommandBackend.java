package delivery.mobile.adb;

import java.util.List;

/**
 * Runs allowlisted {@code adb} argument arrays. Implementations must not invoke a shell;
 * each token is a separate process argument.
 */
public interface AdbCommandBackend {
    /**
     * Execute {@code adb <args...>} with a bounded deadline.
     *
     * @param args arguments after the adb executable (e.g. {@code devices}, {@code -l})
     * @return combined stdout/stderr text (may be truncated by the supervisor)
     */
    String execute(List<String> args) throws AdbInventoryException;
}
