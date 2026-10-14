package delivery.mobile.appium;

/**
 * Preflight result codes exposed to admission / AE (M1-06).
 * {@link #UNAUTHORIZED} is the wire/outcome name; contracts.md lists DEVICE_UNAUTHORIZED.
 */
public enum PreflightOutcome {
    OK,
    DEVICE_OFFLINE,
    UNAUTHORIZED,
    APPIUM_UNAVAILABLE
}
