package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Proven step evidence for Execute results panel (M2-07 / contracts.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StepResultEvidence(
        String stepId,
        String attemptId,
        CaseOutcomeView outcome,
        String failureCode,
        String expectedRef,
        String observedRef,
        boolean observedRedacted,
        int matchCount,
        String preSnapshotId,
        String postSnapshotId,
        String actionOutcome,
        long settleElapsedMs,
        boolean settleCapHit,
        long timingMs,
        ProviderKind providerUsed,
        String providerFallbackReason,
        ProofKind proofKind,
        LocatorResolutionEvidence locator,
        String failureEvidenceRef
) {
    public StepResultEvidence {
        stepId = stepId == null ? "" : stepId;
        attemptId = attemptId == null ? "" : attemptId;
        outcome = outcome == null ? CaseOutcomeView.TODO : outcome;
        failureCode = failureCode == null ? "" : failureCode;
        expectedRef = expectedRef == null ? "" : expectedRef;
        if (observedRedacted) {
            observedRef = LocatorResolutionEvidence.REDACTED;
        } else {
            observedRef = observedRef == null ? "" : observedRef;
        }
        preSnapshotId = preSnapshotId == null ? "" : preSnapshotId;
        postSnapshotId = postSnapshotId == null ? "" : postSnapshotId;
        actionOutcome = actionOutcome == null ? "" : actionOutcome;
        providerUsed = providerUsed == null ? ProviderKind.NONE : providerUsed;
        providerFallbackReason = providerFallbackReason == null ? "" : providerFallbackReason;
        proofKind = proofKind == null ? ProofKind.NONE : proofKind;
        failureEvidenceRef = failureEvidenceRef == null ? "" : failureEvidenceRef;
        if (matchCount < 0) {
            matchCount = 0;
        }
        if (timingMs < 0) {
            timingMs = 0;
        }
        if (settleElapsedMs < 0) {
            settleElapsedMs = 0;
        }
    }
}
