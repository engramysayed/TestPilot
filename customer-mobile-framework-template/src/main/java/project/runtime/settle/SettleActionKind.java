package project.runtime.settle;

/** Action kinds that appear in the shared SETTLE_AFTER table (AE parity). */
public enum SettleActionKind {
    TAP,
    LONG_PRESS,
    BACK,
    TYPE,
    TYPE_SECRET,
    SCROLL_UNTIL,
    SWIPE,
    CLEAR,
    OTHER;

    public static SettleActionKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        try {
            return SettleActionKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
