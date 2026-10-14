package delivery.settle;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes a {@link SettlePolicy.Rule}: poll the shared probe until every condition holds, or the timeout.
 * A timeout is reported (unmet conditions listed), never thrown, so the caller decides; it is never PASS.
 * Network idle only sees fetch/XHR started after the probe first ran plus resource-timing entries.
 */
public final class SettleWaiter {
    static final String PROBE_JS = resource("/keel/settle-probe.js");

    public record Report(boolean settled, long elapsedMs, List<String> unmet, String url) {
    }

    private SettleWaiter() {
    }

    /** Call before the action: installs trackers and returns the URL for url=same/changed checks. */
    public static String prime(WebDriver driver, SettlePolicy policy) {
        String url = String.valueOf(probe(driver, policy).get("url"));
        NetworkScope scope = NetworkScope.fromConfig();
        if (scope.page()) {
            try { scope.sample(driver); } catch (RuntimeException ignored) { /* best effort: installed on first await poll */ }
        }
        return url;
    }

    public static Report await(WebDriver driver, SettlePolicy policy, SettlePolicy.Action action, String urlBefore) {
        SettlePolicy.Rule r = policy.rule(action);
        NetworkScope scope = NetworkScope.fromConfig();
        long t0 = System.currentTimeMillis();
        List<String> unmet;
        String url;
        do {
            Map<String, Object> p;
            try {
                p = probe(driver, policy);
                if (scope.page() && r.network() == SettlePolicy.Network.IDLE) {
                    // r3: page-scoped network numbers replace the probe's (which see every request)
                    Map<String, Object> n = scope.sample(driver);
                    p = new java.util.HashMap<>(p);
                    p.put("pending", n.get("pending"));
                    p.put("netIdleMs", n.get("netIdleMs"));
                }
            } catch (RuntimeException navigating) {
                p = null; // document swapped mid-probe: not settled yet
            }
            unmet = new ArrayList<>();
            url = p == null ? "" : String.valueOf(p.get("url"));
            // Quiet windows start no earlier than the action: reading too early sees the old, "stable" page.
            long elapsed = System.currentTimeMillis() - t0;
            if (p == null) unmet.add("probe");
            else {
                if (!"complete".equals(p.get("ready")) && r.network() == SettlePolicy.Network.IDLE) unmet.add("readyState");
                if (r.network() == SettlePolicy.Network.IDLE
                        && (num(p.get("pending")) > 0 || Math.min(num(p.get("netIdleMs")), elapsed) < r.networkIdleMs())) unmet.add("network");
                if (r.domStableMs() > 0 && Math.min(num(p.get("domQuietMs")), elapsed) < r.domStableMs()) unmet.add("dom");
                if (r.animations() && num(p.get("animations")) > 0) unmet.add("animations");
                if (r.spinners() && num(p.get("spinners")) > 0) unmet.add("spinners");
                if (r.url() == SettlePolicy.Url.CHANGED && url.equals(urlBefore)) unmet.add("url-changed");
                if (r.url() == SettlePolicy.Url.SAME && urlBefore != null && !url.equals(urlBefore)) unmet.add("url-same");
            }
            if (unmet.isEmpty()) return new Report(true, System.currentTimeMillis() - t0, List.of(), url);
            // url=same violated is a fact, not a wait condition: report it now, alone. Before r3 the loop only
            // stopped when url-same was the ONLY unmet condition; background traffic kept "network" unmet, so
            // the step ran to the 5 s timeout and reported [network, url-same] (company-machine symptom).
            if (unmet.contains("url-same")) {
                // quiet windows are still open this early; they are moot once the step broke its URL contract
                return new Report(false, System.currentTimeMillis() - t0, List.of("url-same"), url);
            }
            if (System.currentTimeMillis() - t0 >= r.timeoutMs()) break;
            sleep(policy.pollMs());
        } while (true);
        return new Report(false, System.currentTimeMillis() - t0, unmet, url);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> probe(WebDriver driver, SettlePolicy policy) {
        return (Map<String, Object>) ((JavascriptExecutor) driver).executeScript(PROBE_JS, policy.spinnerSelectors());
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = SettleWaiter.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
