package delivery.mobile.runner.execute;

import delivery.mobile.result.FailureEvidenceManifest;

public record CaseResult(
        String tcId,
        CaseOutcome outcome,
        String failureCode,
        FailureEvidenceManifest evidence,
        boolean containsRawSecret
) {
    public CaseResult {
        tcId = tcId == null ? "" : tcId;
        failureCode = failureCode == null ? "" : failureCode;
        containsRawSecret = containsRawSecret;
    }

    public static CaseResult passed(String tcId) {
        return new CaseResult(tcId, CaseOutcome.PASSED, "", null, false);
    }

    public static CaseResult failed(String tcId, String code, FailureEvidenceManifest ev) {
        return new CaseResult(tcId, CaseOutcome.FAILED, code, ev, false);
    }

    public static CaseResult blocked(String tcId, String code) {
        return new CaseResult(tcId, CaseOutcome.BLOCKED, code, null, false);
    }

    public static CaseResult interrupted(String tcId) {
        return new CaseResult(tcId, CaseOutcome.INTERRUPTED_UNCERTAIN, "DEVICE_LOST", null, false);
    }

    public static CaseResult excludedSimulated(String tcId) {
        return new CaseResult(tcId, CaseOutcome.EXCLUDED_SIMULATED, "SIMULATED", null, false);
    }
}
