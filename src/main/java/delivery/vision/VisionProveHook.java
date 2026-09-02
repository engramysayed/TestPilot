package delivery.vision;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.heal.FailedLocator;

import java.util.List;
import java.util.Optional;

/**
 * Layer 1.5 vision bind hook — extracted for unit testing without Spring/WebDriver boot.
 */
public final class VisionProveHook {

    private VisionProveHook() {
    }

    public static Optional<List<ProvenStep>> tryLayer15(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String html,
            byte[] healPng,
            List<ProvenStep> stepBatch,
            boolean bindFailed,
            List<FailedLocator> failedThisIntent,
            AuthoringService authoring,
            GroundingBrowser browser,
            VisionGroundingProvider provider) {
        if (!bindFailed || !VisionGroundingConfig.enabled()
                || !VisionTriggers.isEligible(intent)
                || !VisionTriggers.isWeakBind(stepBatch)) {
            return Optional.empty();
        }
        List<DomCandidate> table = DomCandidateExtractor.extract(html);
        table = StepIntentBinder.withoutFailedLocators(table, failedThisIntent);
        VisionGroundingEngine engine = new VisionGroundingEngine();
        List<ProvenStep> grounded = engine.tryGround(
                tcId, intent, table, provider, browser, healPng, authoring);
        if (!grounded.isEmpty() && grounded.stream().allMatch(ProvenStep::validated)
                && VisionFailedLocatorFilter.stepsAllowed(grounded, failedThisIntent)) {
            return Optional.of(grounded);
        }
        return Optional.empty();
    }
}
