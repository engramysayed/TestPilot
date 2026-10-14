package project.drivers;

import io.appium.java_client.android.AndroidDriver;
import project.config.MobileConfig;

import java.net.URL;
import java.time.Duration;

/**
 * Creates/quits an Appium AndroidDriver session from {@link MobileConfig}.
 * Does not install Appium/SDK; connects to operator-managed serverUrl only.
 * Safe to unit-test: throws a clear message when server URL is blank / unreachable config.
 */
public class AndroidDriverFactory {

    private AndroidDriver driver;

    /**
     * Builds options and opens a session. Fails with a clear operator message when
     * {@code mobile.serverUrl} is missing/blank — never silently picks a device.
     */
    public AndroidDriver create(MobileConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config required");
        }
        String url = config.properties().get("mobile.serverUrl");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Configure local Appium: set mobile.serverUrl (e.g. http://127.0.0.1:4723) "
                            + "and mobile.udid before creating a session. "
                            + "This template does not start Appium or install the SDK.");
        }
        URL server = config.serverUrlAsUrl();
        try {
            driver = new AndroidDriver(server, config.toUiAutomator2Options());
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            return driver;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to create AndroidDriver session against " + url
                            + ". Ensure Appium is running, the device udid is online, and app identity is correct. "
                            + "Cause: " + e.getMessage(),
                    e);
        }
    }

    /** Builds options without opening a session — for unit tests / diagnostics. */
    public Object buildOptions(MobileConfig config) {
        return config.toUiAutomator2Options();
    }

    public AndroidDriver get() {
        return driver;
    }

    public void quitQuietly() {
        AndroidDriver d = driver;
        driver = null;
        if (d != null) {
            try {
                d.quit();
            } catch (RuntimeException ignored) {
                // never kill unrelated ADB/Appium
            }
        }
    }
}
