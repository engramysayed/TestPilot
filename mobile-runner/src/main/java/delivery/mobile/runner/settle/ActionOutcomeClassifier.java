package delivery.mobile.runner.settle;

/**
 * Classifies pre/post settle evidence into CHANGED / CHANGED_VISUAL / NO_CHANGE.
 * Hierarchy comparison uses normalized fingerprint (system-bar/IME excluded via caller).
 */
public final class ActionOutcomeClassifier {
    private final ScreenshotDiffThreshold threshold;

    public ActionOutcomeClassifier(ScreenshotDiffThreshold threshold) {
        this.threshold = threshold == null ? ScreenshotDiffThreshold.provisional() : threshold;
    }

    public ActionOutcomeClassifier() {
        this(ScreenshotDiffThreshold.provisional());
    }

    /**
     * @param hierarchyChanged normalized hierarchy fingerprint differs
     * @param screenshotChangedRatio fraction of unmasked pixels that differ; null if no screenshot
     */
    public ActionOutcome classify(boolean hierarchyChanged, Double screenshotChangedRatio) {
        if (hierarchyChanged) return ActionOutcome.CHANGED;
        if (screenshotChangedRatio != null && screenshotChangedRatio >= threshold.minChangedRatio()) {
            return ActionOutcome.CHANGED_VISUAL;
        }
        return ActionOutcome.NO_CHANGE;
    }

    public ScreenshotDiffThreshold threshold() {
        return threshold;
    }
}
