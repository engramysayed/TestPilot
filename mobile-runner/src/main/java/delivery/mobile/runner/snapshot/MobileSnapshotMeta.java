package delivery.mobile.runner.snapshot;

/**
 * Capture metadata for a hierarchy snapshot (sequence / time / geometry).
 * Portal does not create Appium sessions — AE fills these after page-source fetch.
 */
public record MobileSnapshotMeta(
        long sequence,
        long capturedAtEpochMs,
        String packageName,
        String activity,
        String context,
        int orientationDegrees,
        int windowWidth,
        int windowHeight,
        String locale,
        MobileSnapshotInvalidation invalidation
) {
    public MobileSnapshotMeta {
        if (sequence < 0) throw new IllegalArgumentException("sequence >= 0");
        if (capturedAtEpochMs < 0) throw new IllegalArgumentException("capturedAtEpochMs >= 0");
        packageName = packageName == null ? "" : packageName;
        activity = activity == null ? "" : activity;
        context = context == null ? "NATIVE" : context;
        locale = locale == null ? "" : locale;
        invalidation = invalidation == null ? MobileSnapshotInvalidation.NONE : invalidation;
    }

    public MobileSnapshotMeta withInvalidation(MobileSnapshotInvalidation reason) {
        return new MobileSnapshotMeta(sequence, capturedAtEpochMs, packageName, activity, context,
                orientationDegrees, windowWidth, windowHeight, locale, reason);
    }

    public boolean isValid() {
        return invalidation == MobileSnapshotInvalidation.NONE;
    }
}
