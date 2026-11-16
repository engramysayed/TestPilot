package delivery.agent.harness.recovery;

import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import delivery.heal.FailedLocator;
import delivery.job.ConversionJobRequest;

import java.util.List;

/** Inputs at the prove intent failure boundary (after heal pipeline when applicable). */
public record ExecutionRecoveryEscalationRequest(
        ConversionJobRequest jobRequest,
        ManualTestCase testCase,
        StepIntentBinder.IntentLine intent,
        int intentIndex,
        String originalFailure,
        String healSkipReason,
        String healTier,
        boolean healPipelineExhausted,
        boolean ollamaHealAttempted,
        boolean cursorHealAttempted,
        String evidenceDir,
        String screenshotPath,
        String currentUrl,
        List<FailedLocator> failedLocators,
        String deduplicationKey) {
}