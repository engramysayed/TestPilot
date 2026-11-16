package delivery.agent.harness.recovery;

import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import delivery.heal.FailedLocator;
import delivery.job.ConversionJobRequest;
import delivery.privacy.SecretSanitizer;
import delivery.vision.VisionGroundingConfig;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionInvestigationEvidenceBuilder {
    private static final int STEPS_EXCERPT_MAX = 500;

    private ExecutionInvestigationEvidenceBuilder() {
    }

    public static ExecutionInvestigationEvidenceV1 build(ExecutionRecoveryEscalationRequest request) {
        ManualTestCase tc = request.testCase();
        StepIntentBinder.IntentLine intent = request.intent();
        String scrubbedFailure = scrub(request.originalFailure(), request.jobRequest());
        String scrubbedHealSkip = scrub(request.healSkipReason(), request.jobRequest());

        List<String> missing = new ArrayList<>();
        missing.add("stackTrace");
        missing.add("consoleLog");
        missing.add("networkErrors");
        if (request.currentUrl() == null || request.currentUrl().isBlank()) {
            missing.add("currentUrl");
        }
        if (request.screenshotPath() == null || request.screenshotPath().isBlank()) {
            missing.add("screenshotPath");
        }

        List<ExecutionInvestigationEvidenceV1.FailedLocatorRef> locs = new ArrayList<>();
        if (request.failedLocators() != null) {
            for (FailedLocator f : request.failedLocators()) {
                if (f != null) {
                    locs.add(new ExecutionInvestigationEvidenceV1.FailedLocatorRef(
                            safe(f.strategy()), safe(f.value()), safe(f.errorSummary())));
                }
            }
        }

        String steps = tc == null || tc.steps() == null ? "" : tc.steps();
        String excerpt = steps.length() <= STEPS_EXCERPT_MAX ? steps : steps.substring(0, STEPS_EXCERPT_MAX) + "…";

        return new ExecutionInvestigationEvidenceV1(
                ExecutionInvestigationEvidenceV1.SCHEMA_VERSION,
                ExecutionInvestigationEvidenceV1.CONTRACT_ID,
                tc == null ? "" : safe(tc.tcId()),
                tc == null ? "" : safe(tc.expectedResult()),
                excerpt,
                request.intentIndex(),
                intent == null ? "" : safe(intent.text()),
                intent == null || intent.kind() == null ? "" : intent.kind().name(),
                ExecutionFailureClassifier.classify(intent, scrubbedFailure, scrubbedHealSkip),
                scrubbedFailure,
                scrubbedHealSkip,
                request.healPipelineExhausted(),
                request.healTier() == null ? "none" : request.healTier(),
                new ExecutionInvestigationEvidenceV1.HealingAttempts(
                        request.ollamaHealAttempted(),
                        request.cursorHealAttempted(),
                        VisionGroundingConfig.enabled()),
                request.currentUrl(),
                request.evidenceDir(),
                request.screenshotPath(),
                List.copyOf(locs),
                List.copyOf(missing));
    }

    private static String scrub(String text, ConversionJobRequest request) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (request == null) {
            return text;
        }
        String user = request.username() == null ? "" : request.username();
        String pass = request.password() == null ? "" : request.password();
        if (user.isBlank() && pass.isEmpty()) {
            return text;
        }
        return SecretSanitizer.scrubText(text, java.util.List.of(user, pass));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}