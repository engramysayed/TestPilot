package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * FE/BE-facing mobile case result (M2-07). Stable view over runner CaseResult.
 * Does not embed raw secrets; evidence is refs / sanitized manifests only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileCaseResultView(
        String caseId,
        String attemptId,
        CaseOutcomeView outcome,
        String failureCode,
        SetupOutcome setupOutcome,
        ProofKind proofKind,
        ProviderKind providerUsed,
        String providerFallbackReason,
        long timingMs,
        FailureEvidenceManifest evidence,
        ObservedAppIdentity observedApp,
        ObservedDeviceIdentity observedDevice,
        List<LocatorResolutionEvidence> locatorEvidence,
        List<StepResultEvidence> steps,
        boolean containsRawSecret
) {
    public MobileCaseResultView {
        caseId = caseId == null ? "" : caseId;
        attemptId = attemptId == null ? "" : attemptId;
        outcome = outcome == null ? CaseOutcomeView.TODO : outcome;
        failureCode = failureCode == null ? "" : failureCode;
        setupOutcome = setupOutcome == null ? SetupOutcome.NOT_REQUIRED : setupOutcome;
        proofKind = proofKind == null ? ProofKind.NONE : proofKind;
        providerUsed = providerUsed == null ? ProviderKind.NONE : providerUsed;
        providerFallbackReason = providerFallbackReason == null ? "" : providerFallbackReason;
        observedApp = observedApp == null ? ObservedAppIdentity.empty() : observedApp;
        observedDevice = observedDevice == null ? ObservedDeviceIdentity.empty() : observedDevice;
        locatorEvidence = locatorEvidence == null ? List.of() : List.copyOf(locatorEvidence);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (timingMs < 0) {
            timingMs = 0;
        }
        if (containsRawSecret) {
            throw new IllegalArgumentException("MobileCaseResultView must not carry containsRawSecret=true");
        }
    }

    public static MobileCaseResultView passed(String caseId, String attemptId) {
        return new MobileCaseResultView(
                caseId, attemptId, CaseOutcomeView.PASSED, "", SetupOutcome.NOT_REQUIRED,
                ProofKind.ASSERTION, ProviderKind.KEEL, "", 0L, null,
                ObservedAppIdentity.empty(), ObservedDeviceIdentity.empty(),
                List.of(), List.of(), false);
    }

    public static MobileCaseResultView failed(
            String caseId, String attemptId, String code, FailureEvidenceManifest ev) {
        return new MobileCaseResultView(
                caseId, attemptId, CaseOutcomeView.FAILED, code, SetupOutcome.NOT_REQUIRED,
                ProofKind.HIERARCHY, ProviderKind.KEEL, "", 0L, ev,
                ObservedAppIdentity.empty(), ObservedDeviceIdentity.empty(),
                List.of(), List.of(), false);
    }

    public static MobileCaseResultView blocked(String caseId, String attemptId, String code) {
        return new MobileCaseResultView(
                caseId, attemptId, CaseOutcomeView.BLOCKED, code, SetupOutcome.FAILED,
                ProofKind.NONE, ProviderKind.NONE, "", 0L, null,
                ObservedAppIdentity.empty(), ObservedDeviceIdentity.empty(),
                List.of(), List.of(), false);
    }

    public static MobileCaseResultView interrupted(String caseId, String attemptId) {
        return new MobileCaseResultView(
                caseId, attemptId, CaseOutcomeView.INTERRUPTED_UNCERTAIN, "DEVICE_LOST",
                SetupOutcome.UNKNOWN, ProofKind.NONE, ProviderKind.NONE, "", 0L, null,
                ObservedAppIdentity.empty(), ObservedDeviceIdentity.empty(),
                List.of(), List.of(), false);
    }
}
