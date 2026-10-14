package delivery.authoring;

import delivery.codegen.ProvenStep;

import java.util.List;

public sealed interface PrecisionBatchBindOutcome permits PrecisionBatchBindOutcome.BatchBound,
        PrecisionBatchBindOutcome.FallbackKeel {

    record IntentSteps(int batchIndex, List<ProvenStep> steps) {
    }

    record BatchBound(List<IntentSteps> boundInOrder, String tier) implements PrecisionBatchBindOutcome {
    }

    record FallbackKeel(String reason) implements PrecisionBatchBindOutcome {
    }
}
