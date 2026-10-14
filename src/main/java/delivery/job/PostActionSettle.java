package delivery.job;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.LogsManager;

import java.time.Duration;

/**
 * Configurable pause after a successful action so the next intent binds against a settled page.
 * Override with env or system property {@code DELIVERY_POST_ACTION_WAIT_MS} (default 5000).
 */
public final class PostActionSettle {
    public static final String PROPERTY = "DELIVERY_POST_ACTION_WAIT_MS";
    public static final String ADAPTIVE_PROPERTY = "DELIVERY_ADAPTIVE_SETTLE";
    public static final long DEFAULT_MS = 5000L;
    public static final long NAV_EXTRA_MS = 1500L;

    private PostActionSettle() {
    }

    public static long waitMs() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(PROPERTY);
        }
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MS;
        }
        try {
            long ms = Long.parseLong(raw.trim());
            return Math.max(0L, ms);
        } catch (NumberFormatException e) {
            return DEFAULT_MS;
        }
    }

    /** Sleep for {@link #waitMs()} and log. Interruptible; restores interrupt flag. */
    public static void afterAction() {
        sleep(waitMs(), "POST_ACTION_SETTLE");
    }

    /** Field entry and assertions already wait in the executor; only transitions need settling. */
    public static void afterSteps(WebDriver driver, java.util.List<delivery.codegen.ProvenStep> steps) {
        if (steps == null || steps.stream().noneMatch(s -> "click".equalsIgnoreCase(s.action())
                || "navigate".equalsIgnoreCase(s.action()) || "select".equalsIgnoreCase(s.action()))) return;
        if (driver instanceof JavascriptExecutor && Boolean.parseBoolean(System.getProperty(ADAPTIVE_PROPERTY,
                System.getenv().getOrDefault(ADAPTIVE_PROPERTY, "true")))) {
            adaptiveWait(driver);
        } else afterNavigation(driver, null, "click");
    }

    /**
     * Stronger settle after navigation-like clicks (cart, checkout, continue, finish):
     * base wait + document ready (+ optional URL change wait).
     */
    public static void afterNavigation(WebDriver driver, String urlBefore, String intentText) {
        long started = System.nanoTime();
        try {
            if (Boolean.parseBoolean(System.getProperty(ADAPTIVE_PROPERTY,
                    System.getenv().getOrDefault(ADAPTIVE_PROPERTY, "false"))) && driver instanceof JavascriptExecutor) {
                adaptiveWait(driver);
                return;
            }
            legacyNavigationWait(driver, urlBefore, intentText);
        } finally {
            LogsManager.info("PERF settle elapsedMs=" + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    static final String READINESS_SCRIPT = """
        if (!window.__keelSettle || window.__keelSettle.root !== document.documentElement) {
          if (window.__keelSettle) window.__keelSettle.observer.disconnect();
          const state = {root: document.documentElement, changed: performance.now()};
          state.observer = new MutationObserver(() => { state.changed = performance.now(); });
          state.observer.observe(document.documentElement, {subtree:true, childList:true, attributes:true, characterData:true});
          window.__keelSettle = state;
        }
        return document.readyState === 'complete'
          && !document.querySelector('[aria-busy="true"]')
          && performance.now() - window.__keelSettle.changed >= 500;
        """;

    private static void adaptiveWait(WebDriver driver) {
        long budget = waitMs();
        if (budget <= 0) return;
        long started = System.nanoTime();
        try {
            new WebDriverWait(driver, Duration.ofMillis(budget))
                    .pollingEvery(Duration.ofMillis(100))
                    .until(d -> {
                        Object ready = ((JavascriptExecutor) d).executeScript(READINESS_SCRIPT);
                        return Boolean.TRUE.equals(ready)
                                && java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= Math.min(750, budget);
                    });
        } catch (org.openqa.selenium.TimeoutException timeout) {
            LogsManager.info("SETTLE_BUDGET_EXHAUSTED: readiness unconfirmed; subsequent step must validate");
        }
        // Transport/session/script errors deliberately surface rather than claiming readiness.
    }

    private static void legacyNavigationWait(WebDriver driver, String urlBefore, String intentText) {
        long ms = waitMs();
        if (looksLikeNavigation(intentText)) {
            ms += NAV_EXTRA_MS;
        }
        sleep(ms, "POST_ACTION_SETTLE_NAV");
        if (driver == null) {
            return;
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> {
                try {
                    Object rs = ((JavascriptExecutor) d).executeScript("return document.readyState");
                    return "complete".equals(String.valueOf(rs));
                } catch (RuntimeException e) {
                    return true;
                }
            });
            if (urlBefore != null && !urlBefore.isBlank()) {
                new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
                    try {
                        String now = d.getCurrentUrl();
                        return now != null && (!now.equals(urlBefore) || looksSettledEnough(intentText));
                    } catch (RuntimeException e) {
                        return true;
                    }
                });
            }
        } catch (RuntimeException e) {
            LogsManager.info("POST_ACTION_SETTLE_NAV: wait ended: " + e.getMessage());
        }
    }

    static boolean looksLikeNavigation(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return false;
        }
        String t = intentText.toLowerCase();
        return t.contains("checkout")
                || t.contains("cart")
                || t.contains("continue")
                || t.contains("finish")
                || t.contains("submit")
                || t.contains("login")
                || t.contains("sign in")
                || t.contains("otp")
                || t.contains("verify");
    }

    /** Soft landing when SPA keeps the same URL (rare on SauceDemo). */
    private static boolean looksSettledEnough(String intentText) {
        return intentText != null && intentText.toLowerCase().contains("assert");
    }

    private static void sleep(long ms, String label) {
        if (ms <= 0L) {
            return;
        }
        LogsManager.info(label + ": ms=" + ms);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
