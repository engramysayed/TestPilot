package delivery.mobile.appium;

/**
 * Diagnostics-only preflight: no install/reset/session side effects.
 */
public final class MobilePreflightResult {
    private final PreflightOutcome outcome;
    private final String message;
    private final String serial;
    private final String adbState;
    private final boolean appiumReachable;

    public MobilePreflightResult(PreflightOutcome outcome,
                                 String message,
                                 String serial,
                                 String adbState,
                                 boolean appiumReachable) {
        this.outcome = outcome == null ? PreflightOutcome.APPIUM_UNAVAILABLE : outcome;
        this.message = message == null ? "" : message;
        this.serial = serial;
        this.adbState = adbState;
        this.appiumReachable = appiumReachable;
    }

    public static MobilePreflightResult ok(String serial, String adbState) {
        return new MobilePreflightResult(PreflightOutcome.OK, "ok", serial, adbState, true);
    }

    public boolean isOk() {
        return outcome == PreflightOutcome.OK;
    }

    public PreflightOutcome getOutcome() { return outcome; }
    public String getMessage() { return message; }
    public String getSerial() { return serial; }
    public String getAdbState() { return adbState; }
    public boolean isAppiumReachable() { return appiumReachable; }

    /** Outcome code string for APIs / AE (UNAUTHORIZED not DEVICE_UNAUTHORIZED). */
    public String codeName() {
        return outcome.name();
    }
}
