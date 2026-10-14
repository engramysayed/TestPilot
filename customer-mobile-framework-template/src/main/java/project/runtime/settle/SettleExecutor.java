package project.runtime.settle;

/**
 * Applies SETTLE_AFTER rules via {@link SettleProbe} — no blocking sleep.
 */
public final class SettleExecutor {
    private final SettleAfterTable table;
    private final ActionOutcomeClassifier classifier;

    public SettleExecutor(SettleAfterTable table, ActionOutcomeClassifier classifier) {
        this.table = table == null ? SettleAfterTable.defaults() : table;
        this.classifier = classifier == null ? new ActionOutcomeClassifier() : classifier;
    }

    public SettleExecutor() {
        this(SettleAfterTable.defaults(), new ActionOutcomeClassifier());
    }

    public ActionOutcome settle(SettleActionKind kind, String preFingerprint, SettleProbe probe) {
        SettleRule rule = table.ruleFor(kind);
        if (rule.mode() == SettleRule.SettleMode.NONE || rule.capMs() <= 0) {
            String post = probe.hierarchyFingerprint();
            boolean changed = preFingerprint != null && !preFingerprint.equals(post);
            return classifier.classify(changed, probe.screenshotChangedRatio());
        }
        long budget = rule.capMs();
        long spent = 0;
        String last = preFingerprint == null ? "" : preFingerprint;
        boolean sawChange = false;
        while (spent < budget) {
            long step = Math.min(50L, budget - spent);
            spent += probe.awaitPoll(step);
            String now = probe.hierarchyFingerprint();
            if (!now.equals(last)) {
                sawChange = true;
                if (rule.mode() == SettleRule.SettleMode.HELD_STILL) {
                    // need two identical consecutive reads
                    String confirm = probe.hierarchyFingerprint();
                    spent += probe.awaitPoll(Math.min(50L, Math.max(0, budget - spent)));
                    if (confirm.equals(now)) {
                        return classifier.classify(true, probe.screenshotChangedRatio());
                    }
                    last = confirm;
                    continue;
                }
                // AFTER_CHANGE_THEN_HELD: after first change, wait for held still
                last = now;
                String held = probe.hierarchyFingerprint();
                if (held.equals(now)) {
                    return classifier.classify(true, probe.screenshotChangedRatio());
                }
                last = held;
                continue;
            }
            if (rule.mode() == SettleRule.SettleMode.HELD_STILL && spent > 0) {
                return classifier.classify(sawChange, probe.screenshotChangedRatio());
            }
            last = now;
        }
        String post = probe.hierarchyFingerprint();
        boolean changed = sawChange || (preFingerprint != null && !preFingerprint.equals(post));
        return classifier.classify(changed, probe.screenshotChangedRatio());
    }
}
