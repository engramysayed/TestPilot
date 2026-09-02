package delivery.ir;

import delivery.codegen.ProvenStep;

import java.util.List;

/**
 * Durable Phase-1 intermediate representation for one Excel TC.
 * Written under {@code delivery-work/.../ir/{tcId}.json} after prove.
 */
public record TcDraft(
        String tcId,
        String title,
        String stepsText,
        String expectedResult,
        TcDraftStatus status,
        List<ProvenStep> provenSteps,
        List<ProvenStep> loginSteps,
        boolean needsLoginBeforeMethod,
        int blockerStepIndex,
        String blockerIntent,
        String failureReason,
        String evidenceDir,
        int retryCountOnBlocker,
        String lastPageUrl,
        /** none | ollama | cursor — highest heal tier used on blocker (or last heal). */
        String healTier,
        String healSkipReason,
        /** Login form URL stem for reclustering login steps (D13). */
        String loginFormUrl
) {
    public TcDraft {
        provenSteps = provenSteps == null ? List.of() : List.copyOf(provenSteps);
        loginSteps = loginSteps == null ? List.of() : List.copyOf(loginSteps);
        title = title == null ? "" : title;
        stepsText = stepsText == null ? "" : stepsText;
        expectedResult = expectedResult == null ? "" : expectedResult;
        failureReason = failureReason == null ? "" : failureReason;
        evidenceDir = evidenceDir == null ? "" : evidenceDir;
        blockerIntent = blockerIntent == null ? "" : blockerIntent;
        lastPageUrl = lastPageUrl == null ? "" : lastPageUrl;
        healTier = healTier == null || healTier.isBlank() ? "none" : healTier;
        healSkipReason = healSkipReason == null ? "" : healSkipReason;
        loginFormUrl = loginFormUrl == null ? "" : loginFormUrl;
    }

    /** Compatibility constructor without heal / login-form metadata. */
    public TcDraft(
            String tcId,
            String title,
            String stepsText,
            String expectedResult,
            TcDraftStatus status,
            List<ProvenStep> provenSteps,
            List<ProvenStep> loginSteps,
            boolean needsLoginBeforeMethod,
            int blockerStepIndex,
            String blockerIntent,
            String failureReason,
            String evidenceDir,
            int retryCountOnBlocker,
            String lastPageUrl
    ) {
        this(tcId, title, stepsText, expectedResult, status, provenSteps, loginSteps,
                needsLoginBeforeMethod, blockerStepIndex, blockerIntent, failureReason,
                evidenceDir, retryCountOnBlocker, lastPageUrl, "none", "", "");
    }

    public TcDraft withHeal(String tier, String skipReason) {
        return new TcDraft(
                tcId, title, stepsText, expectedResult, status, provenSteps, loginSteps,
                needsLoginBeforeMethod, blockerStepIndex, blockerIntent, failureReason,
                evidenceDir, retryCountOnBlocker, lastPageUrl,
                tier, skipReason, loginFormUrl);
    }

    public TcDraft withLoginFormUrl(String url) {
        return new TcDraft(
                tcId, title, stepsText, expectedResult, status, provenSteps, loginSteps,
                needsLoginBeforeMethod, blockerStepIndex, blockerIntent, failureReason,
                evidenceDir, retryCountOnBlocker, lastPageUrl,
                healTier, healSkipReason, url);
    }
}
