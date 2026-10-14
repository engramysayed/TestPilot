package project.runtime.gestures;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import project.runtime.waits.MobileWait;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;

/**
 * SCROLL_UNTIL / SWIPE / BACK / LONG_PRESS helpers.
 * Bounded maxSwipes / maxNoProgress from properties; visibilityPercentage default 50.
 * No blocking sleep.
 */
public final class MobileGestures {
    private MobileGestures() {}

    public static void scrollUntil(AndroidDriver driver, By container, String direction, By target) {
        scrollUntil(driver, container, direction, target, 5, 2, 50);
    }

    public static void scrollUntil(
            AndroidDriver driver,
            By container,
            String direction,
            By target,
            int maxSwipes,
            int maxNoProgress,
            int visibilityPercentage) {
        if (driver == null || target == null) {
            throw new IllegalArgumentException("driver/target required");
        }
        int swipes = 0;
        int noProgress = 0;
        String lastFp = "";
        while (swipes < Math.max(1, maxSwipes)) {
            try {
                WebElement el = driver.findElement(target);
                if (el.isDisplayed()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // not found yet
            }
            String fp = safeSource(driver);
            swipe(driver, direction, 400);
            swipes++;
            if (fp.equals(lastFp)) {
                noProgress++;
                if (noProgress >= Math.max(1, maxNoProgress)) {
                    throw new IllegalStateException(
                            "SCROLL_LIMIT: no progress after " + noProgress
                                    + " swipes (maxNoProgress). Tune mobile.scroll.maxSwipes / maxNoProgress "
                                    + "or visibilityPercentage=" + visibilityPercentage);
                }
            } else {
                noProgress = 0;
            }
            lastFp = fp;
        }
        throw new IllegalStateException(
                "SCROLL_LIMIT: target not found after " + maxSwipes
                        + " swipes. Tune scroll bounds or locator.");
    }

    public static void swipe(AndroidDriver driver, String direction, double durationMs) {
        if (driver == null) {
            throw new IllegalArgumentException("driver required");
        }
        String dir = direction == null ? "UP" : direction.trim().toUpperCase(Locale.ROOT);
        var size = driver.manage().window().getSize();
        int cx = size.width / 2;
        int cy = size.height / 2;
        int startX = cx;
        int startY = cy;
        int endX = cx;
        int endY = cy;
        int delta = (int) (size.height * 0.35);
        switch (dir) {
            case "UP" -> {
                startY = cy + delta / 2;
                endY = cy - delta / 2;
            }
            case "DOWN" -> {
                startY = cy - delta / 2;
                endY = cy + delta / 2;
            }
            case "LEFT" -> {
                startX = cx + delta / 2;
                endX = cx - delta / 2;
                delta = (int) (size.width * 0.35);
            }
            case "RIGHT" -> {
                startX = cx - delta / 2;
                endX = cx + delta / 2;
                delta = (int) (size.width * 0.35);
            }
            default -> throw new IllegalArgumentException("Unknown swipe direction: " + direction);
        }
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        long ms = Math.max(50L, (long) durationMs);
        swipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), startX, startY));
        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(ms), PointerInput.Origin.viewport(), endX, endY));
        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(swipe));
    }

    public static void longPress(AndroidDriver driver, By locator) {
        if (driver == null || locator == null) {
            throw new IllegalArgumentException("driver/locator required");
        }
        WebElement el = MobileWait.untilActionable(driver, locator);
        new Actions(driver).clickAndHold(el).pause(Duration.ofMillis(800)).release().perform();
    }

    public static void pressBack(AndroidDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("driver required");
        }
        driver.navigate().back();
    }

    private static String safeSource(AndroidDriver driver) {
        try {
            String src = driver.getPageSource();
            return src == null ? "" : Integer.toHexString(src.hashCode());
        } catch (RuntimeException e) {
            return "";
        }
    }
}
