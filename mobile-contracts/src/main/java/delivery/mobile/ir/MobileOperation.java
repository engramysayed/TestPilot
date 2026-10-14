package delivery.mobile.ir;

import java.util.Locale;

/** v1 mobile operations. Unknown ops are rejected (not ignored). */
public enum MobileOperation {
    TAP,
    TYPE,
    CLEAR,
    LONG_PRESS,
    SCROLL_UNTIL,
    SWIPE,
    PRESS_BACK,
    HIDE_KEYBOARD,
    SWITCH_CONTEXT,
    ASSERT,
    CAPTURE_TEXT,
    COMPARE_CAPTURED,
    WAIT;

    public static MobileOperation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MobileIrException("OP_REQUIRED", "operation is required");
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return MobileOperation.valueOf(u);
        } catch (IllegalArgumentException e) {
            throw new MobileIrException("OP_UNSUPPORTED", "unsupported operation: " + raw);
        }
    }
}
