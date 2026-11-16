package delivery.agent.harness.recovery;

import delivery.authoring.StepIntentBinder;
import delivery.job.ProvePhase;
import delivery.portal.AgentHarnessProperties;

/** Escalation policy — distinct from {@link ExecutionFailureClassifier}. */
public final class ExecutionRecoveryEligibility {
    private ExecutionRecoveryEligibility() {
    }

    public static boolean harnessFlagsEnabled(AgentHarnessProperties properties) {
        if (properties == null) {
            return false;
        }
        return properties.isEnabled()
                && properties.isExecutionRecoveryCursorEscalationEnabled();
    }

    public static boolean shouldInvestigate(ExecutionRecoveryEscalationRequest request) {
        if (request == null) {
            return false;
        }
        if (request.jobRequest() == null || request.testCase() == null) {
            return false;
        }
        if (request.originalFailure() == null || request.originalFailure().isBlank()) {
            return false;
        }
        StepIntentBinder.IntentLine intent = request.intent();
        if (intent != null && !ProvePhase.mayHeal(intent, request.originalFailure())) {
            return false;
        }
        return request.healPipelineExhausted();
    }
}