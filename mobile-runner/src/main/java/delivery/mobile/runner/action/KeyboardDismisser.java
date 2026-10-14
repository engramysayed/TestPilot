package delivery.mobile.runner.action;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * HIDE_KEYBOARD gate (M3-04 / arch §6): check {@code isKeyboardShown}; if shown, ESC then BACK;
 * poll ≤1s after each; <strong>never send BACK when keyboard is hidden</strong>.
 */
public final class KeyboardDismisser {
    public static final long POLL_BUDGET_MS = 1_000;
    private static final int MAX_SPINS = 200;

    private KeyboardDismisser() {
    }

    public record Result(boolean dismissed, boolean backSent, boolean escSent, String detail) {
        public static Result alreadyHidden() {
            return new Result(true, false, false, "keyboard already hidden");
        }
    }

    public static Result dismiss(MobileDriverPort driver, LongSupplier monoMs) throws MobileDriverPort.DriverException {
        if (!driver.isKeyboardShown()) {
            return Result.alreadyHidden();
        }
        driver.pressKeyCode(MobileDriverPort.KEYCODE_ESC);
        if (waitUntilHidden(driver, monoMs)) {
            return new Result(true, false, true, "dismissed via ESC");
        }
        driver.pressKeyCode(MobileDriverPort.KEYCODE_BACK);
        if (waitUntilHidden(driver, monoMs)) {
            return new Result(true, true, true, "dismissed via BACK fallback");
        }
        return new Result(false, true, true, "KEYBOARD_NOT_DISMISSED");
    }

    private static boolean waitUntilHidden(MobileDriverPort driver, LongSupplier monoMs) {
        long start = monoMs.getAsLong();
        for (int i = 0; i < MAX_SPINS; i++) {
            if (!driver.isKeyboardShown()) {
                return true;
            }
            long now = monoMs.getAsLong();
            if (now - start >= POLL_BUDGET_MS) {
                break;
            }
        }
        return !driver.isKeyboardShown();
    }

    static boolean pollHidden(BooleanSupplier shown, LongSupplier monoMs, long budgetMs) {
        long start = monoMs.getAsLong();
        for (int i = 0; i < MAX_SPINS; i++) {
            if (!shown.getAsBoolean()) {
                return true;
            }
            if (monoMs.getAsLong() - start >= budgetMs) {
                break;
            }
        }
        return !shown.getAsBoolean();
    }
}
