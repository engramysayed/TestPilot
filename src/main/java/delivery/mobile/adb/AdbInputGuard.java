package delivery.mobile.adb;

import java.util.regex.Pattern;

/**
 * Rejects malicious or ambiguous serial/package tokens before any process is spawned.
 * Tokens are always passed as discrete argv elements; this guard is defense-in-depth.
 */
public final class AdbInputGuard {
    /** Typical adb serials: emulator-5554, USB ids, IP:port. No whitespace or shell metacharacters. */
    private static final Pattern SAFE_SERIAL = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    /** Android application id (ASCII Java-style). Unicode is not accepted as a package token. */
    private static final Pattern SAFE_PACKAGE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$");

    private AdbInputGuard() {
    }

    public static String requireSerial(String serial) throws AdbInventoryException {
        if (serial == null || serial.isBlank()) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_SERIAL, "ADB serial is required");
        }
        String trimmed = serial.trim();
        if (!trimmed.equals(serial) || serial.indexOf('\n') >= 0 || serial.indexOf('\r') >= 0) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_SERIAL, "ADB serial contains whitespace");
        }
        if (trimmed.startsWith("-")) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_SERIAL, "ADB serial must not look like a flag");
        }
        if (!SAFE_SERIAL.matcher(trimmed).matches()) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_SERIAL, "ADB serial rejected");
        }
        return trimmed;
    }

    public static String requirePackageName(String packageName) throws AdbInventoryException {
        if (packageName == null || packageName.isBlank()) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_PACKAGE, "package name is required");
        }
        String trimmed = packageName.trim();
        if (!trimmed.equals(packageName)
                || packageName.indexOf('\n') >= 0
                || packageName.indexOf('\r') >= 0
                || packageName.indexOf(' ') >= 0) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_PACKAGE, "package name contains whitespace");
        }
        if (!SAFE_PACKAGE.matcher(trimmed).matches() || trimmed.length() > 255) {
            throw new AdbInventoryException(AdbErrorCode.INVALID_PACKAGE, "package name rejected");
        }
        return trimmed;
    }
}
