package project.config;

import io.appium.java_client.android.options.UiAutomator2Options;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds UiAutomator2Options from {@link MobileProperties}.
 * Only real Appium capabilities receive the appium: namespace;
 * mobile.auth.*, vision, evidence are Keel runtime settings — not forwarded blindly.
 * <p>POM pins remain PLACEHOLDER until M0-02 lock flips to qualified; draft pins used only in helper-tests.
 */
public final class MobileConfig {

    private final MobileProperties properties;

    public MobileConfig(MobileProperties properties) {
        this.properties = Objects.requireNonNull(properties);
        properties.validatePlatform();
    }

    public MobileProperties properties() {
        return properties;
    }

    public String serverUrl() {
        return properties.serverUrl();
    }

    public URL serverUrlAsUrl() {
        try {
            return URI.create(serverUrl()).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("mobile.serverUrl is not a valid URL: " + serverUrl(), e);
        }
    }

    public String udid() {
        return properties.udid();
    }

    /** Capability map for diagnostics / tests (no secrets). */
    public Map<String, Object> capabilityMap() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("platformName", properties.platformName());
        caps.put("appium:automationName", properties.automationName());
        caps.put("appium:udid", properties.udid());
        caps.put("appium:deviceName", properties.deviceName());
        caps.put("appium:noReset", properties.noReset());
        caps.put("appium:fullReset", properties.fullReset());
        caps.put("appium:newCommandTimeout", properties.commandTimeoutSeconds());
        caps.put("appium:adbExecTimeout", properties.adbTimeoutSeconds() * 1000L);
        String source = properties.appSource();
        if ("APK".equals(source)) {
            String path = properties.get("mobile.appPath");
            if (path != null && !path.isBlank()) {
                caps.put("appium:app", path);
            }
        } else {
            putIfPresent(caps, "appium:appPackage", properties.get("mobile.appPackage"));
            putIfPresent(caps, "appium:appActivity", properties.get("mobile.appActivity"));
            putIfPresent(caps, "appium:appWaitActivity", properties.get("mobile.appWaitActivity"));
        }
        // Never autoGrantPermissions by default
        caps.put("appium:autoGrantPermissions", false);
        return caps;
    }

    public UiAutomator2Options toUiAutomator2Options() {
        UiAutomator2Options opts = new UiAutomator2Options();
        opts.setPlatformName(properties.platformName());
        opts.setAutomationName(properties.automationName());
        opts.setUdid(properties.udid());
        opts.setDeviceName(properties.deviceName());
        opts.setNoReset(properties.noReset());
        opts.setFullReset(properties.fullReset());
        opts.setNewCommandTimeout(Duration.ofSeconds(properties.commandTimeoutSeconds()));
        opts.setAdbExecTimeout(Duration.ofSeconds(properties.adbTimeoutSeconds()));
        String source = properties.appSource();
        if ("APK".equals(source)) {
            String path = properties.get("mobile.appPath");
            if (path != null && !path.isBlank()) {
                opts.setApp(path);
            }
        } else {
            String pkg = properties.get("mobile.appPackage");
            String act = properties.get("mobile.appActivity");
            if (pkg != null && !pkg.isBlank()) {
                opts.setAppPackage(pkg);
            }
            if (act != null && !act.isBlank()) {
                opts.setAppActivity(act);
            }
            String wait = properties.get("mobile.appWaitActivity");
            if (wait != null && !wait.isBlank()) {
                opts.setAppWaitActivity(wait);
            }
        }
        opts.setAutoGrantPermissions(false);
        // Proposed UiA2 capture settings (qualify in M0-02)
        opts.setCapability("appium:settings[waitForIdleTimeout]", 100);
        opts.setCapability("appium:settings[enableMultiWindows]", true);
        opts.setCapability("appium:settings[ignoreUnimportantViews]", false);
        return opts;
    }

    public Duration lookupTimeout() {
        return Duration.ofSeconds(properties.lookupTimeoutSeconds());
    }

    public Duration sessionStartTimeout() {
        return Duration.ofSeconds(properties.sessionStartTimeoutSeconds());
    }

    private static void putIfPresent(Map<String, Object> caps, String key, String value) {
        if (value != null && !value.isBlank()) {
            caps.put(key, value);
        }
    }
}
