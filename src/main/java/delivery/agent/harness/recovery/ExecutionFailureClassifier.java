package delivery.agent.harness.recovery;

import delivery.authoring.StepIntentBinder;

import java.util.Locale;

/** Heuristic classification from prove/heal failure text and intent kind — no fabricated labels. */
public final class ExecutionFailureClassifier {
    private ExecutionFailureClassifier() {
    }

    public static ExecutionFailureClass classify(
            StepIntentBinder.IntentLine intent,
            String failureReason,
            String healSkipReason) {
        String combined = (failureReason == null ? "" : failureReason)
                + " "
                + (healSkipReason == null ? "" : healSkipReason);
        String lower = combined.toLowerCase(Locale.ROOT);

        if (intent != null && intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
            return ExecutionFailureClass.ASSERTION_OR_EXPECTED_BEHAVIOR;
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("wait")) {
            return ExecutionFailureClass.SYNCHRONIZATION_TIMEOUT;
        }
        if (lower.contains("stale") || lower.contains("not interactable") || lower.contains("click intercepted")) {
            return ExecutionFailureClass.ELEMENT_INTERACTION;
        }
        if (lower.contains("no dom candidate")
                || lower.contains("locator")
                || lower.contains("no such element")
                || lower.contains("unable to locate")
                || lower.contains("bind")) {
            return ExecutionFailureClass.LOCATOR_RESOLUTION;
        }
        if (lower.contains("login_failed")
                || lower.contains("unexpected")
                || lower.contains("wrong page")
                || lower.contains("session")) {
            return ExecutionFailureClass.UNEXPECTED_APPLICATION_STATE;
        }
        if (lower.contains("browser")
                || lower.contains("driver")
                || lower.contains("navigate failed")
                || lower.contains("session died")
                || lower.contains("infra")) {
            return ExecutionFailureClass.BROWSER_OR_INFRASTRUCTURE;
        }
        if (lower.contains("assert") || lower.contains("expected")) {
            return ExecutionFailureClass.ASSERTION_OR_EXPECTED_BEHAVIOR;
        }
        if (combined.isBlank()) {
            return ExecutionFailureClass.UNKNOWN;
        }
        return ExecutionFailureClass.UNKNOWN;
    }
}