package delivery.mobile.runner.settle;

import delivery.mobile.runner.bind.BindingFingerprint;
import delivery.mobile.runner.snapshot.MobileSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Post-action settle using the shared {@link SettleAfterTable}.
 * Toast/snackbar assertions consume {@link SettleResult#toastWindowSnapshots()}.
 */
public final class MobileSettleService {
    private final SettleAfterTable table;
    private final ActionOutcomeClassifier classifier;
    private final long pollMs;
    private final LongSupplier monoMs;
    private final boolean sleepOnPoll;

    public MobileSettleService(
            SettleAfterTable table,
            ActionOutcomeClassifier classifier,
            long pollMs,
            LongSupplier monoMs,
            boolean sleepOnPoll
    ) {
        this.table = Objects.requireNonNull(table);
        this.classifier = Objects.requireNonNull(classifier);
        this.pollMs = Math.max(1, pollMs);
        this.monoMs = monoMs == null ? System::currentTimeMillis : monoMs;
        this.sleepOnPoll = sleepOnPoll;
    }

    /** Production defaults: 200ms poll, wall clock, sleep between polls. */
    public MobileSettleService() {
        this(SettleAfterTable.defaults(), new ActionOutcomeClassifier(), 200L, System::currentTimeMillis, true);
    }

    /** Test helper: no thread sleep; clock advanced by test. */
    public static MobileSettleService forTests(LongSupplier monoMs) {
        return new MobileSettleService(SettleAfterTable.defaults(), new ActionOutcomeClassifier(), 200L, monoMs, false);
    }

    public SettleResult settle(
            SettleActionKind kind,
            MobileSnapshot pre,
            HierarchyProbe probe,
            ScreenshotProbe screenshots,
            byte[] preScreenshot
    ) {
        SettleRule rule = table.ruleFor(kind);
        String preFp = BindingFingerprint.of(pre);
        List<MobileSnapshot> toastWindow = new ArrayList<>();
        toastWindow.add(pre);

        if (rule.mode() == SettleRule.SettleMode.NONE) {
            MobileSnapshot post = probe == null ? pre : probe.capture();
            toastWindow.add(post);
            return new SettleResult(ActionOutcome.NO_CHANGE, 0L, false, preFp, BindingFingerprint.of(post),
                    toastWindow, null);
        }

        long start = monoMs.getAsLong();
        long deadline = start + rule.capMs();
        boolean needChangeFirst = rule.mode() == SettleRule.SettleMode.AFTER_CHANGE_THEN_HELD;
        boolean sawChange = !needChangeFirst;

        String prevFp = null;
        String lastFp = preFp;
        boolean capHit = false;
        int polls = 0;
        final int maxPolls = (int) Math.max(2, rule.capMs() / pollMs + 3);

        while (polls < maxPolls) {
            long now = monoMs.getAsLong();
            if (now >= deadline) {
                capHit = true;
                break;
            }
            if (probe == null) break;
            if (sleepOnPoll && polls > 0) {
                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            MobileSnapshot cur = probe.capture();
            polls++;
            toastWindow.add(cur);
            String fp = BindingFingerprint.of(cur);
            if (!fp.equals(preFp)) {
                sawChange = true;
            }
            if (prevFp != null && fp.equals(prevFp) && sawChange) {
                lastFp = fp;
                break; // held still: two consecutive identical after required change (or held-only)
            }
            prevFp = fp;
            lastFp = fp;
        }
        if (polls >= maxPolls && monoMs.getAsLong() >= deadline) {
            capHit = true;
        }

        Double ratio = null;
        if (screenshots != null && preScreenshot != null) {
            ratio = ScreenshotDiffer.changedRatio(preScreenshot, screenshots.capture(), 4);
        }
        boolean hierarchyChanged = !preFp.equals(lastFp);
        ActionOutcome outcome = classifier.classify(hierarchyChanged, ratio);
        long elapsed = Math.max(0L, monoMs.getAsLong() - start);
        return new SettleResult(outcome, elapsed, capHit, preFp, lastFp, toastWindow, ratio);
    }
}
