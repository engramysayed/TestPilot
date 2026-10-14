package delivery.mobile.runner.settle;

import delivery.mobile.runner.snapshot.MobileSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SettleResult {
    private final ActionOutcome outcome;
    private final long elapsedMs;
    private final boolean capHit;
    private final String preFingerprint;
    private final String postFingerprint;
    private final List<MobileSnapshot> toastWindowSnapshots;
    private final Double screenshotChangedRatio;

    public SettleResult(
            ActionOutcome outcome,
            long elapsedMs,
            boolean capHit,
            String preFingerprint,
            String postFingerprint,
            List<MobileSnapshot> toastWindowSnapshots,
            Double screenshotChangedRatio
    ) {
        this.outcome = outcome;
        this.elapsedMs = elapsedMs;
        this.capHit = capHit;
        this.preFingerprint = preFingerprint == null ? "" : preFingerprint;
        this.postFingerprint = postFingerprint == null ? "" : postFingerprint;
        this.toastWindowSnapshots = toastWindowSnapshots == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(toastWindowSnapshots));
        this.screenshotChangedRatio = screenshotChangedRatio;
    }

    public ActionOutcome outcome() { return outcome; }
    public long elapsedMs() { return elapsedMs; }
    public boolean capHit() { return capHit; }
    public String preFingerprint() { return preFingerprint; }
    public String postFingerprint() { return postFingerprint; }
    public List<MobileSnapshot> toastWindowSnapshots() { return toastWindowSnapshots; }
    public Double screenshotChangedRatio() { return screenshotChangedRatio; }
}
