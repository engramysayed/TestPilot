package project.config;

/**
 * Readable preflight — device/Appium/app readiness diagnostics only.
 * Never installs Appium/SDK/ChromeDriver on the customer's behalf.
 */
public final class PrerequisiteChecker {

    private PrerequisiteChecker() {}

    public static void checkOrThrow(MobileConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config");
        }
        // Soft checks usable without a device: required identity keys
        MobileProperties props = config.properties();
        props.validatePlatform();
        String server = props.get("mobile.serverUrl");
        if (server == null || server.isBlank()) {
            throw new IllegalStateException(
                    "Prerequisite failed: mobile.serverUrl is blank. "
                            + "Start Appium locally and set the URL before running tests.");
        }
        // App identity: validate shape when provided; INSTALLED/APK checked when keys present
        String source = props.get("mobile.appSource");
        if (source != null && !source.isBlank()) {
            try {
                props.validateAppSource();
            } catch (IllegalStateException e) {
                throw new IllegalStateException("Prerequisite failed: " + e.getMessage(), e);
            }
        }
    }
}
