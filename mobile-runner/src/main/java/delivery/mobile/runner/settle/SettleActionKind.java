package delivery.mobile.runner.settle;

/** Action kinds that appear in the shared SETTLE_AFTER table. */
public enum SettleActionKind {
    TAP,
    LONG_PRESS,
    BACK,
    TYPE,
    TYPE_SECRET,
    SCROLL_UNTIL,
    SWIPE,
    CLEAR,
    HIDE_KEYBOARD,
    LAUNCH,
    RESTART,
    OTHER
}
