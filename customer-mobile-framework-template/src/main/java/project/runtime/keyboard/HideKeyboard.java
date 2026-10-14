package project.runtime.keyboard;

import io.appium.java_client.android.AndroidDriver;
import project.runtime.waits.MobileWait;

/**
 * isKeyboardShown → ESC (111) → if still shown BACK (4); never BACK when hidden.
 * Poll ≤1s each via MobileWait — no blocking sleep.
 */
public final class HideKeyboard {
    private HideKeyboard() {}

    public static void hide(AndroidDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("driver required");
        }
        if (!isKeyboardShown(driver)) {
            return;
        }
        try {
            driver.pressKey(new io.appium.java_client.android.nativekey.KeyEvent(
                    io.appium.java_client.android.nativekey.AndroidKey.ESCAPE));
        } catch (RuntimeException e) {
            // fall through to BACK
        }
        MobileWait.pollQuietly(driver, 200);
        if (!isKeyboardShown(driver)) {
            return;
        }
        try {
            driver.pressKey(new io.appium.java_client.android.nativekey.KeyEvent(
                    io.appium.java_client.android.nativekey.AndroidKey.BACK));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to dismiss obstructing keyboard", e);
        }
        MobileWait.pollQuietly(driver, 200);
        if (isKeyboardShown(driver)) {
            throw new IllegalStateException("Keyboard still shown after ESC+BACK");
        }
    }

    public static boolean isKeyboardShown(AndroidDriver driver) {
        try {
            return driver.isKeyboardShown();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
