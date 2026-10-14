package delivery.mobile.appium;

/**
 * M1-06 Appium lifecycle / preflight codes (portal bookkeeping; no Appium jars).
 * Preflight outcomes align with contracts: {@code DEVICE_OFFLINE}, {@code UNAUTHORIZED}
 * (maps to {@code DEVICE_UNAUTHORIZED}), {@code APPIUM_UNAVAILABLE}.
 */
public enum AppiumErrorCode {
    DEVICE_OFFLINE,
    /** Preflight outcome name; contracts enum is DEVICE_UNAUTHORIZED. */
    UNAUTHORIZED,
    APPIUM_UNAVAILABLE,
    SESSION_START_TIMEOUT,
    CANCELLED_WHILE_STARTING,
    PROCESS_CRASH,
    CLEANUP_FAILED,
    PORT_EXHAUSTED,
    NOT_OWNED,
    INVALID_STATE,
    FORBIDDEN_KILL
}
