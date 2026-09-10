package delivery.vision;

import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;

import java.util.List;

public final class VisionTriggers {

    private VisionTriggers() {
    }

    public static boolean isEligible(StepIntentBinder.IntentLine intent) {
        if (intent == null) {
            return false;
        }
        return switch (intent.kind()) {
            case CLICK, CLICK_LOGIN, TYPE_FIELD, TYPE_USER, TYPE_PASS -> true;
            case ASSERT_VISIBLE -> false;
        };
    }

    public static boolean isWeakBind(List<ProvenStep> stepBatch) {
        if (stepBatch == null || stepBatch.isEmpty()) {
            return true;
        }
        for (ProvenStep step : stepBatch) {
            if (!step.validated()) {
                return true;
            }
            String rationale = step.rationale() == null ? "" : step.rationale();
            if (rationale.startsWith("AMBIGUOUS:")) {
                return true;
            }
            if (rationale.contains("Weak candidate match")) {
                return true;
            }
            if (rationale.contains("No DOM candidate")) {
                return true;
            }
            if (rationale.contains("No distinctive-token")) {
                return true;
            }
            if (rationale.contains("No action control matching")) {
                return true;
            }
        }
        return false;
    }
}
