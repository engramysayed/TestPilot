package delivery.mobile.adb;

/** Portal/runner ADB inventory rejection codes (architecture § device discovery). */
public enum AdbErrorCode {
    /** adb executable missing or failed to start. */
    SDK_UNAVAILABLE,
    /** Selected serial not present in {@code adb devices -l}. */
    DEVICE_MISSING,
    /** Device present but unauthorized. */
    DEVICE_UNAUTHORIZED,
    /** Device present but offline. */
    DEVICE_OFFLINE,
    /** Serial rejected before any process spawn (injection / empty). */
    INVALID_SERIAL,
    /** Package name rejected before any process spawn. */
    INVALID_PACKAGE,
    /** Package not installed on the selected serial. */
    PACKAGE_NOT_FOUND,
    /** Bounded adb command failed or timed out. */
    COMMAND_FAILED
}
