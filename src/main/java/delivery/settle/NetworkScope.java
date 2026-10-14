package delivery.settle;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * r3: which requests count for the settle network signal. Background traffic the page did not start
 * (Chrome-internal update/safebrowsing/optimization-guide, extensions, corporate proxies or security tools,
 * third-party beacons) must not keep "network idle" from settling.
 *
 * <pre>
 * delivery.settle.network.scope  = page | all   (default page; all = legacy A8 probe numbers)
 * delivery.settle.network.ignore = comma list of URL substrings or re:&lt;regex&gt;
 * delivery.settle.network.allow  = comma list of extra origins that count (e.g. https://api.example.com)
 * </pre>
 * page counts only http(s) requests to the page's origin (or an allowed origin) that the page document
 * starts; see {@code keel/settle-network.js}. The frozen v1 probe and table are unchanged.
 */
public record NetworkScope(boolean page, List<String> ignore, List<String> allow) {
    public static final String SCOPE = "delivery.settle.network.scope";
    public static final String IGNORE = "delivery.settle.network.ignore";
    public static final String ALLOW = "delivery.settle.network.allow";
    static final String JS = resource("/keel/settle-network.js");

    public static NetworkScope fromConfig() {
        return new NetworkScope(!"all".equalsIgnoreCase(prop(SCOPE, "page")), list(prop(IGNORE, "")), list(prop(ALLOW, "")));
    }

    public Map<String, Object> arg() {
        return Map.of("scope", page ? "page" : "all", "ignore", ignore, "allow", allow);
    }

    /** Installs the page tracker (idempotent) and returns {pending, netIdleMs, counted, ignored}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sample(WebDriver driver) {
        return (Map<String, Object>) ((JavascriptExecutor) driver).executeScript(JS, arg());
    }

    static List<String> list(String v) {
        return v == null || v.isBlank() ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String prop(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            try { v = utils.PropertyReader.getProperty(key); } catch (RuntimeException | Error ignored) { v = null; }
        }
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String resource(String path) {
        try (InputStream in = NetworkScope.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
