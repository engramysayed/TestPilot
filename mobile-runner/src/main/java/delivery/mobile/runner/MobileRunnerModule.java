package delivery.mobile.runner;

/**
 * Module marker for the isolated worker classpath (Appium lives here, not in the portal).
 */
public final class MobileRunnerModule {
    public static final String ARTIFACT_ID = "mobile-runner";
    /** Proposed Appium Java client pin; live qualify is QA-owned. */
    public static final String PROPOSED_APPIUM_JAVA_CLIENT = "10.1.1";
    /** Proposed Selenium pin for mobile-runtime only; web portal stays on 4.49.0. */
    public static final String PROPOSED_SELENIUM = "4.43.0";

    private MobileRunnerModule() {
    }
}
