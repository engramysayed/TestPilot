package delivery.agent.harness.recovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned investigation evidence (references only for large artifacts). */
public record ExecutionInvestigationEvidenceV1(
        int schemaVersion,
        String contractId,
        String testCaseId,
        String expectedResult,
        String stepsExcerpt,
        Integer intentIndex,
        String intentText,
        String intentKind,
        ExecutionFailureClass failureClass,
        String originalFailure,
        String healSkipReason,
        boolean healPipelineExhausted,
        String healTier,
        HealingAttempts healingAttempts,
        String currentUrl,
        String evidenceDir,
        String screenshotPath,
        /** Dir containing per-step IR JSON (prior steps' intents, pass/fail, screenshots). Optional. */
        String irDir,
        List<FailedLocatorRef> failedLocators,
        List<String> missingFields) {

    public static final String CONTRACT_ID = "keel.execution-investigation-evidence/v1";
    public static final int SCHEMA_VERSION = 1;

    public record HealingAttempts(
            boolean ollamaAttempted,
            boolean cursorHealAttempted,
            boolean visionLayerEnabled) {
    }

    public record FailedLocatorRef(String strategy, String value, String errorSummary) {
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", schemaVersion);
        m.put("contractId", contractId);
        m.put("testCaseId", testCaseId);
        m.put("expectedResult", expectedResult);
        m.put("stepsExcerpt", stepsExcerpt);
        if (intentIndex != null) {
            m.put("intentIndex", intentIndex);
        }
        m.put("intentText", intentText);
        m.put("intentKind", intentKind);
        m.put("failureClass", failureClass.name());
        m.put("originalFailure", originalFailure);
        m.put("healSkipReason", healSkipReason);
        m.put("healPipelineExhausted", healPipelineExhausted);
        m.put("healTier", healTier);
        Map<String, Object> attempts = new LinkedHashMap<>();
        attempts.put("ollamaAttempted", healingAttempts.ollamaAttempted());
        attempts.put("cursorHealAttempted", healingAttempts.cursorHealAttempted());
        attempts.put("visionLayerEnabled", healingAttempts.visionLayerEnabled());
        m.put("healingAttempts", attempts);
        if (currentUrl != null && !currentUrl.isBlank()) {
            m.put("currentUrl", currentUrl);
        }
        if (evidenceDir != null && !evidenceDir.isBlank()) {
            m.put("evidenceDir", evidenceDir);
        }
        if (screenshotPath != null && !screenshotPath.isBlank()) {
            m.put("screenshotPath", screenshotPath);
        }
        if (irDir != null && !irDir.isBlank()) {
            m.put("irDir", irDir);
        }
        m.put("failedLocators", failedLocators.stream()
                .map(f -> Map.of(
                        "strategy", f.strategy(),
                        "value", f.value(),
                        "errorSummary", f.errorSummary()))
                .toList());
        m.put("missingFields", missingFields);
        return m;
    }
}