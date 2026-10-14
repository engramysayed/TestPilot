package delivery.mobile.runner.action;

import delivery.mobile.runner.snapshot.MobileSnapshot;

/**
 * Narrow port for native actions. Production wires Appium; tests use a fake.
 * {@link #typeUnicode} is the only legal path for non-ASCII (never adb input text).
 * M3-04 adds gesture/keyboard/lifecycle methods.
 */
public interface MobileDriverPort {
    int KEYCODE_ESC = 111;
    int KEYCODE_BACK = 4;

    MobileSnapshot pageSource();

    void tap(int nodeIndex) throws DriverException;

    void clear(int nodeIndex) throws DriverException;

    void typeUnicode(int nodeIndex, String value) throws DriverException;

    void typeAscii(int nodeIndex, String value) throws DriverException;

    String readText(int nodeIndex) throws DriverException;

    byte[] screenshot() throws DriverException;

    // --- M3-04 ---

    default void longPress(int nodeIndex) throws DriverException {
        throw new DriverException(DriverException.Kind.OTHER, "longPress not implemented");
    }

    default void pressBack() throws DriverException {
        throw new DriverException(DriverException.Kind.OTHER, "pressBack not implemented");
    }

    default void pressKeyCode(int keyCode) throws DriverException {
        throw new DriverException(DriverException.Kind.OTHER, "pressKeyCode not implemented");
    }

    default boolean isKeyboardShown() {
        return false;
    }

    default void swipeNormalized(NormalizedSwipe swipe) throws DriverException {
        throw new DriverException(DriverException.Kind.OTHER, "swipeNormalized not implemented");
    }

    default void launchApp(String packageName, String activity) throws DriverException {
        throw new DriverException(DriverException.Kind.OTHER, "launchApp not implemented");
    }

    /**
     * Restart app. {@code clearData} must be explicit — M3-04 restart never implies wipe.
     */
    default void restartApp(String packageName, boolean clearData) throws DriverException {
        throw new DriverException(DriverException.Kind.OTHER, "restartApp not implemented");
    }

    default int windowWidth() {
        return 1080;
    }

    default int windowHeight() {
        return 1920;
    }

    default String orientation() {
        return "PORTRAIT";
    }

    /** Cooperative cancellation flag checked between scroll swipes. */
    default boolean isActionCancelled() {
        return false;
    }

    final class DriverException extends Exception {
        public enum Kind { STALE, TRANSPORT, HIDDEN, DISABLED, CANCELLED, OTHER }
        private final Kind kind;
        public DriverException(Kind kind, String msg) {
            super(msg);
            this.kind = kind;
        }
        public Kind kind() { return kind; }
    }
}
