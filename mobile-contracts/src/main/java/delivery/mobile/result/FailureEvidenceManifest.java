package delivery.mobile.result;

import java.util.List;

/**
 * Sanitized evidence manifest for a failed mobile step (M2-08).
 * Artifact refs only — no raw secrets.
 */
public record FailureEvidenceManifest(
        String stepId,
        String failureCode,
        boolean partial,
        long budgetMs,
        long elapsedMs,
        String redactedXmlRef,
        String maskedScreenshotRef,
        List<NearestCandidateRef> nearestCandidates,
        String logcatExcerptRef,
        boolean appCrashed,
        boolean healSkipped,
        boolean visionSkipped
) {
    public FailureEvidenceManifest {
        nearestCandidates = nearestCandidates == null ? List.of() : List.copyOf(nearestCandidates);
        redactedXmlRef = redactedXmlRef == null ? "" : redactedXmlRef;
        maskedScreenshotRef = maskedScreenshotRef == null ? "" : maskedScreenshotRef;
        logcatExcerptRef = logcatExcerptRef == null ? "" : logcatExcerptRef;
        failureCode = failureCode == null ? "" : failureCode;
        stepId = stepId == null ? "" : stepId;
    }

    public record NearestCandidateRef(
            int rank,
            int nodeIndex,
            String strategyHint,
            String label,
            int editDistance
    ) {}
}
