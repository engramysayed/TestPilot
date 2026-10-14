package project.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Typed view of mobile.* keys (design §4 / appium.properties).
 * Precedence applied by {@link project.utils.dataReader.PropertyReader}: CLI -D → local → defaults.
 * Secrets never land here — use {@link SecretResolver}.
 */
public final class MobileProperties {

    private final Map<String, String> values;

    public MobileProperties(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values)));
    }

    public static MobileProperties fromMap(Map<String, String> values) {
        return new MobileProperties(values);
    }

    public String get(String key) {
        return values.get(key);
    }

    public String getOrDefault(String key, String def) {
        String v = values.get(key);
        return v == null || v.isBlank() ? def : v;
    }

    public String require(String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return v;
    }

    public int intOrDefault(String key, int def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Property " + key + " must be an integer: " + v);
        }
    }

    public boolean boolOrDefault(String key, boolean def) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return Boolean.parseBoolean(v.trim());
    }

    public Map<String, String> asMap() {
        return values;
    }

    /** Validate Android / UiAutomator2 and required mobile.udid. */
    public void validatePlatform() {
        String platform = Objects.toString(get("mobile.platformName"), "");
        String automation = Objects.toString(get("mobile.automationName"), "");
        if (!"Android".equalsIgnoreCase(platform)) {
            throw new IllegalStateException("mobile.platformName must be Android for this template (got: "
                    + platform + ")");
        }
        if (!"UiAutomator2".equalsIgnoreCase(automation)) {
            throw new IllegalStateException("mobile.automationName must be UiAutomator2 for this template (got: "
                    + automation + ")");
        }
        require("mobile.udid");
    }

    public void validateAppSource() {
        String source = getOrDefault("mobile.appSource", "INSTALLED").trim().toUpperCase(Locale.ROOT);
        if ("APK".equals(source)) {
            require("mobile.appPath");
            // appSha256 recommended; not hard-required in scaffold so local debug can omit
        } else if ("INSTALLED".equals(source)) {
            require("mobile.appPackage");
            require("mobile.appActivity");
        } else {
            throw new IllegalStateException("mobile.appSource must be INSTALLED or APK (got: " + source + ")");
        }
    }

    public String platformName() {
        return getOrDefault("mobile.platformName", "Android");
    }

    public String automationName() {
        return getOrDefault("mobile.automationName", "UiAutomator2");
    }

    public String serverUrl() {
        return require("mobile.serverUrl");
    }

    public String udid() {
        return require("mobile.udid");
    }

    public String deviceName() {
        return getOrDefault("mobile.deviceName", udid());
    }

    public String appSource() {
        return getOrDefault("mobile.appSource", "INSTALLED").trim().toUpperCase(Locale.ROOT);
    }

    public int commandTimeoutSeconds() {
        return intOrDefault("mobile.commandTimeoutSeconds", 120);
    }

    public int lookupTimeoutSeconds() {
        return intOrDefault("mobile.lookupTimeoutSeconds", 15);
    }

    public int sessionStartTimeoutSeconds() {
        return intOrDefault("mobile.sessionStartTimeoutSeconds", 120);
    }

    public int adbTimeoutSeconds() {
        return intOrDefault("mobile.adbTimeoutSeconds", 30);
    }

    public int scrollMaxSwipes() {
        return intOrDefault("mobile.scroll.maxSwipes", 5);
    }

    public int scrollMaxNoProgress() {
        return intOrDefault("mobile.scroll.maxNoProgress", 2);
    }

    public boolean noReset() {
        return boolOrDefault("mobile.noReset", true);
    }

    public boolean fullReset() {
        return boolOrDefault("mobile.fullReset", false);
    }

    public boolean clearData() {
        return boolOrDefault("mobile.reset.clearData", false);
    }
}
