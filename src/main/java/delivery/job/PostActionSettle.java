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

    /**
     * Stronger settle after navigation-like clicks (cart, checkout, continue, finish):
     * base wait + document ready (+ optional URL change wait).
     */
    public static void afterNavigation(WebDriver driver, String urlBefore, String intentText) {
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
