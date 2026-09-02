package delivery.vision;

import delivery.authoring.DomCandidate;
import delivery.authoring.StepIntentBinder;
import delivery.heal.FailedLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VisionHealSupport {

    private VisionHealSupport() {
    }

    public static Optional<GroundingHit> tryBboxHit(
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            VisionGroundingProvider provider,
            GroundingBrowser browser,
            List<FailedLocator> failed) {
        if (!VisionGroundingConfig.enabled() || provider == null || browser == null) {
            return Optional.empty();
        }
        Optional<ViewportSweep.SweepHit> sweep = ViewportSweep.run(provider, intent, browser);
        if (sweep.isEmpty()) {
            return Optional.empty();
        }
        ViewportMetrics metrics = sweep.get().metrics();
        List<DomCandidate> mutable = new ArrayList<>(candidates);
        VisualCandidate visual = sweep.get().candidate();
        Optional<GroundingHit> hit = ElementGrounder.groundToHit(
                mutable,
                visual,
                browser,
                metrics.screenshotWidth(),
                metrics.screenshotHeight(),
                metrics.innerWidth(),
                metrics.innerHeight());
        Optional<GroundingHit> accepted = VisionFailedLocatorFilter.acceptHit(hit.orElse(null), failed);
        if (accepted.isPresent()) {
            VisionAttemptLog.record(VisionAttempt.of(
                    visual.boundingBox(), visual.confidence(),
                    accepted.get().candidateId(), true, "grounded"));
            // Successes stay in VisionAttemptLog for heal hints only — not the miss journal.
        } else if (hit.isPresent()) {
            VisionAttemptLog.record(VisionAttempt.of(
                    visual.boundingBox(), visual.confidence(),
                    hit.get().candidateId(), false, "filtered"));
            VisionMissJournal.recordGrounding(
                    intent == null ? "" : intent.text(),
                    "filtered",
                    visual.boundingBox(),
                    visual.confidence(),
                    visual.description(),
                    hit.get().candidateId(),
                    null);
        } else {
            VisionAttemptLog.record(VisionAttempt.of(
                    visual.boundingBox(), visual.confidence(), "none", false, "miss"));
            VisionMissJournal.recordGrounding(
                    intent == null ? "" : intent.text(),
                    "miss",
                    visual.boundingBox(),
                    visual.confidence(),
                    visual.description(),
                    "none",
                    null);
        }
        return accepted;
    }
}
