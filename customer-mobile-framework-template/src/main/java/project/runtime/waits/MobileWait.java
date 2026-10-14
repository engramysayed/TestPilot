package project.runtime.waits;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Bounded waits — no blocking sleep. Uses WebDriverWait / driver timeouts only.
 */
public final class MobileWait {
    private static final long DEFAULT_LOOKUP_MS = 15_000L;

    private MobileWait() {}

    public static WebElement untilPresent(AndroidDriver driver, By locator) {
        return untilPresent(driver, locator, DEFAULT_LOOKUP_MS);
    }

    public static WebElement untilPresent(AndroidDriver driver, By locator, long timeoutMs) {
        require(driver, locator);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(Math.max(1, timeoutMs)));
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public static WebElement untilActionable(AndroidDriver driver, By locator) {
        return untilActionable(driver, locator, DEFAULT_LOOKUP_MS);
    }

    public static WebElement untilActionable(AndroidDriver driver, By locator, long timeoutMs) {
        require(driver, locator);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(Math.max(1, timeoutMs)));
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Quiet bounded poll used by settle — advances by invoking a cheap driver call.
     * Returns elapsed budget consumed (capped). Never blocking sleep.
     */
    public static long pollQuietly(AndroidDriver driver, long maxMs) {
        if (maxMs <= 0) {
            return 0L;
        }
        long start = System.nanoTime();
        try {
            // Cheap status touch; if driver is fake/null-safe callers short-circuit earlier
            if (driver != null) {
                driver.getSessionId();
            }
        } catch (RuntimeException ignored) {
            // ignore
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        return Math.min(maxMs, Math.max(1L, elapsedMs));
    }

    private static void require(AndroidDriver driver, By locator) {
        if (driver == null || locator == null) {
            throw new IllegalArgumentException("driver/locator required");
        }
    }
}
