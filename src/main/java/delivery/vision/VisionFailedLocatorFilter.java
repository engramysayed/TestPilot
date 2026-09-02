package delivery.vision;

import delivery.authoring.DomCandidate;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.heal.FailedLocator;

import java.util.List;
import java.util.Optional;

public final class VisionFailedLocatorFilter {

    private VisionFailedLocatorFilter() {
    }

    public static Optional<GroundingHit> acceptHit(GroundingHit hit, List<FailedLocator> failed) {
        if (hit == null) {
            return Optional.empty();
        }
        if (failed == null || failed.isEmpty()) {
            return Optional.of(hit);
        }
        List<DomCandidate> filtered = StepIntentBinder.withoutFailedLocators(hit.table(), failed);
        if (filtered.stream().noneMatch(c -> hit.candidateId().equals(c.id()))) {
            return Optional.empty();
        }
        return Optional.of(new GroundingHit(hit.candidateId(), filtered, hit.added()));
    }

    public static boolean stepsAllowed(List<ProvenStep> steps, List<FailedLocator> failed) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        if (failed == null || failed.isEmpty()) {
            return true;
        }
        for (ProvenStep step : steps) {
            DomCandidate asStep = new DomCandidate(
                    "step",
                    step.locatorStrategy() == null ? "" : step.locatorStrategy(),
                    step.locatorValue() == null ? "" : step.locatorValue(),
                    "",
                    step.locatorValue() == null ? "" : step.locatorValue());
            if (StepIntentBinder.withoutFailedLocators(List.of(asStep), failed).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
