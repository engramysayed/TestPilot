package delivery.vision;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import utils.LogsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VisionGroundingEngine {

    public List<ProvenStep> tryGround(
            String tcId,
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> table,
            VisionGroundingProvider provider,
            GroundingBrowser browser,
            byte[] png,
            AuthoringService authoring) {
        if (!VisionGroundingConfig.enabled()) {
            LogsManager.info("FINAL: MISS");
            return List.of();
        }
        if (!VisionTriggers.isEligible(intent)) {
            LogsManager.info("FINAL: MISS");
            return List.of();
        }
        if (browser == null || authoring == null || provider == null) {
            LogsManager.info("FINAL: MISS");
            return List.of();
        }

        Optional<ViewportSweep.SweepHit> sweep = ViewportSweep.run(provider, intent, browser);
        if (sweep.isEmpty()) {
            LogsManager.info("FINAL: MISS");
            return List.of();
        }

        VisualCandidate visual = sweep.get().candidate();
        ViewportMetrics metrics = sweep.get().metrics();
        int imageW = metrics.screenshotWidth();
        int imageH = metrics.screenshotHeight();
        int viewW = metrics.innerWidth();
        int viewH = metrics.innerHeight();
        List<DomCandidate> mutableTable = new ArrayList<>(table == null ? List.of() : table);

        Optional<GroundingHit> hit = ElementGrounder.groundToHit(
                mutableTable, visual, browser, imageW, imageH, viewW, viewH);
        if (hit.isEmpty()) {
            VisionAttemptLog.record(VisionAttempt.of(
                    visual.boundingBox(), visual.confidence(), "none", false, "miss"));
            LogsManager.info("FINAL: MISS");
            return List.of();
        }
        List<ProvenStep> steps = authoring.stepsPreferringCandidate(
                tcId, intent, hit.get().table(), hit.get().candidateId(), true);
        if (!steps.isEmpty() && steps.stream().allMatch(ProvenStep::validated)) {
            VisionAttemptLog.record(VisionAttempt.of(
                    visual.boundingBox(), visual.confidence(), hit.get().candidateId(), true, "grounded"));
            LogsManager.info("FINAL: READY");
            return steps;
        }
        VisionAttemptLog.record(VisionAttempt.of(
                visual.boundingBox(), visual.confidence(), hit.get().candidateId(), false, "filtered"));
        LogsManager.info("FINAL: MISS");
        return List.of();
    }

}
