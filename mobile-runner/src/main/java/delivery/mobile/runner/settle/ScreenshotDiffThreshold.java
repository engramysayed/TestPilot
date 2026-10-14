package delivery.mobile.runner.settle;

/**
 * Screenshot visual-diff threshold. Proposed default until emulator qualification (M2-04 / M0-02).
 * Ratio of changed unmasked pixels in [0,1].
 */
public record ScreenshotDiffThreshold(double minChangedRatio) {
    public ScreenshotDiffThreshold {
        if (minChangedRatio < 0 || minChangedRatio > 1) {
            throw new IllegalArgumentException("minChangedRatio in [0,1]");
        }
    }

    /** Proposed starting value — recorded as provisional until qualified. */
    public static ScreenshotDiffThreshold provisional() {
        return new ScreenshotDiffThreshold(0.002);
    }
}
