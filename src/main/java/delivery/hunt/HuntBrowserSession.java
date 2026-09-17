package delivery.hunt;

import delivery.job.ConversionJobRequest;
import drivers.WebDriverFactory;
import org.openqa.selenium.WebDriver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the live hunt browser: driver factory, CDP network capture, and capped restarts.
 */
public final class HuntBrowserSession implements HuntBrowserControls, AutoCloseable {
    public static final int MAX_RESTARTS = 2;

    private final String baseUrl;
    private final ConversionJobRequest loginRequest;
    private final boolean hasLoginUsername;
    private final WebDriverFactory driverFactory;
    private final List<String> preferredHooks;
    private HuntNetworkCapture network;
    private int restartCount;

    private HuntBrowserSession(
            String baseUrl,
            ConversionJobRequest loginRequest,
            boolean hasLoginUsername,
            WebDriverFactory driverFactory,
            HuntNetworkCapture network,
            List<String> preferredHooks
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.loginRequest = loginRequest;
        this.hasLoginUsername = hasLoginUsername;
        this.driverFactory = driverFactory;
        this.network = network;
        this.preferredHooks = preferredHooks == null ? List.of() : List.copyOf(preferredHooks);
    }

    public static HuntBrowserSession open(
            String baseUrl,
            ConversionJobRequest loginRequest,
            boolean hasLoginUsername
    ) {
        return open(baseUrl, loginRequest, hasLoginUsername, List.of());
    }

    public static HuntBrowserSession open(
            String baseUrl,
            ConversionJobRequest loginRequest,
            boolean hasLoginUsername,
            List<String> preferredHooks
    ) {
        delivery.net.WorkerPac.installForJob(baseUrl);
        WebDriverFactory factory = new WebDriverFactory();
        HuntNetworkCapture network = HuntNetworkCapture.attach(factory.get());
        return new HuntBrowserSession(baseUrl, loginRequest, hasLoginUsername, factory, network, preferredHooks);
    }

    /** Test helper: enforces restart cap without opening Chrome. */
    static HuntBrowserSession forCapTest(int restartsAlreadyUsed) {
        HuntBrowserSession s = new HuntBrowserSession("", null, false, null, null, List.of());
        s.restartCount = Math.max(0, restartsAlreadyUsed);
        return s;
    }

    public WebDriver driver() {
        return driverFactory.get();
    }

    public WebDriverFactory driverFactory() {
        return driverFactory;
    }

    public HuntNetworkCapture network() {
        return network;
    }

    public String networkStatus() {
        return network == null ? "unsupported" : network.statusLabel();
    }

    public int restartCount() {
        return restartCount;
    }

    public int remainingRestarts() {
        return Math.max(0, MAX_RESTARTS - restartCount);
    }

    /**
     * Quit + new browser, re-attach network, open base URL. {@code login=true} is a hint for the
     * planner only — the server does not auto-login; the hunter must type credentials on the next cycles.
     */
    public Map<String, Object> restart(boolean login) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loginRequested", login);
        if (restartCount >= MAX_RESTARTS) {
            result.put("ok", false);
            result.put("reason", "restart_browser cap=" + MAX_RESTARTS + " already used");
            return result;
        }
        try {
            closeNetworkQuietly();
            driverFactory.restart();
            restartCount++;
            network = HuntNetworkCapture.attach(driverFactory.get());
            WebDriver driver = driverFactory.get();
            if (!baseUrl.isBlank()) {
                driver.get(baseUrl);
            }
            String loginNote = login && hasLoginUsername
                    ? "hunter_performs_login"
                    : "open_only";
            result.put("ok", true);
            result.put("restartsUsed", restartCount);
            result.put("url", safeUrl(driver));
            result.put("loginAttempted", false);
            result.put("loginOk", false);
            result.put("loginNote", loginNote);
            result.put("network", networkStatus());
            return result;
        } catch (Exception e) {
            result.put("ok", false);
            result.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return result;
        }
    }

    @Override
    public void close() {
        closeNetworkQuietly();
        if (driverFactory == null) {
            return;
        }
        try {
            driverFactory.quit();
        } catch (Exception ignored) {
        }
    }

    private void closeNetworkQuietly() {
        if (network == null) {
            return;
        }
        try {
            network.close();
        } catch (Exception ignored) {
        }
        network = null;
    }

    private static String safeUrl(WebDriver driver) {
        try {
            String u = driver.getCurrentUrl();
            return u == null ? "" : u;
        } catch (Exception e) {
            return "";
        }
    }
}
