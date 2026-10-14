package project.runtime.settle;

/**
 * Screenshot visual-diff threshold. Provisional until M0-02 emulator qualification.
 * Ratio of changed unmasked pixels in [0,1].
 */
public record ScreenshotDiffThreshold(double minChangedRatio) {
    public ScreenshotDiffThreshold {
        if (minChangedRatio < 0 || minChangedRatio > 1) {
            throw new IllegalArgumentException("minChangedRatio in [0,1]");
        }
    }

    public static ScreenshotDiffThreshold provisional() {
        return new ScreenshotDiffThreshold(0.002);
    }
}
