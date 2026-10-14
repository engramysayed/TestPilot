package delivery.mobile.runner.snapshot;

/** Reasons a cached snapshot must not be reused (architecture §5). */
public enum MobileSnapshotInvalidation {
    NONE,
    ACTION,
    NAVIGATION,
    SCROLL,
    PERMISSION_DIALOG,
    KEYBOARD,
    ORIENTATION,
    WINDOW_CHANGE,
    MANUAL
}
