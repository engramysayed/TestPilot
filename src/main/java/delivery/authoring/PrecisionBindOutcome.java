package delivery.authoring;

import delivery.codegen.ProvenStep;

import java.util.List;

public sealed interface PrecisionBindOutcome permits PrecisionBindOutcome.Bound,
        PrecisionBindOutcome.NeedsSolve, PrecisionBindOutcome.FallbackKeel {

    record Bound(List<ProvenStep> steps, String tier) implements PrecisionBindOutcome {
    }

    record NeedsSolve(
            List<delivery.authoring.DomCandidate> candidates,
            List<delivery.authoring.DomCandidate> shortlist,
            String table,
            String htmlExcerpt
    ) implements PrecisionBindOutcome {
    }

    record FallbackKeel(String reason) implements PrecisionBindOutcome {
    }
}
