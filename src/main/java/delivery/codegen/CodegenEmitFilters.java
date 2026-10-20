package delivery.codegen;

import delivery.job.TcOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Steps kept in the customer framework ZIP (prove IR may still contain more). */
public final class CodegenEmitFilters {
    private CodegenEmitFilters() {
    }

    public static TcOutcome filterOutcome(TcOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        return new TcOutcome(
                outcome.tcId(),
                outcome.title(),
                outcome.status(),
                filterSteps(outcome.provenSteps()),
                outcome.failureReason(),
                outcome.evidenceDir(),
                outcome.needsLoginBeforeMethod(),
                outcome.loginSteps() == null ? null : filterSteps(outcome.loginSteps()),
                filterSteps(outcome.setupSteps()));
    }

    public static List<ProvenStep> filterSteps(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ProvenStep> out = new ArrayList<>(steps.size());
        for (ProvenStep step : steps) {
            if (emitStep(step)) {
                out.add(step);
            }
        }
        return out;
    }

    static boolean emitStep(ProvenStep step) {
        if (step == null) {
            return false;
        }
        String action = step.action() == null ? "" : step.action().trim().toLowerCase(Locale.ROOT);
        if (!"assert".equals(action)) {
            return true;
        }
        String rationale = step.rationale() == null ? "" : step.rationale();
        if (rationale.contains(":echo") || rationale.contains(":unknown-echo") || rationale.contains(":pageShown")) {
            return false;
        }
        if (rationale.contains("intent:ASSERT_VISIBLE:field=")) {
            return false;
        }
        String loc = step.locatorValue() == null ? "" : step.locatorValue().trim().toLowerCase(Locale.ROOT);
        if ("html".equals(loc) || loc.endsWith("html") && loc.length() <= 12) {
            return false;
        }
        return true;
    }
}
