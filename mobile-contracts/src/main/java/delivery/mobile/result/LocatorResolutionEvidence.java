package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Per-step locator resolution evidence for Execute UI (M2-07).
 * Sensitive locator values are redacted when {@code valueRedacted} is true.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocatorResolutionEvidence(
        String stepId,
        LocatorOriginKind requestedOrigin,
        LocatorOriginKind selectedOrigin,
        String strategy,
        String value,
        boolean valueRedacted,
        String fallbackReason,
        int matchCount
) {
    public static final String REDACTED = "(redacted)";

    public LocatorResolutionEvidence {
        stepId = stepId == null ? "" : stepId;
        requestedOrigin = requestedOrigin == null ? LocatorOriginKind.HIERARCHY : requestedOrigin;
        selectedOrigin = selectedOrigin == null ? LocatorOriginKind.HIERARCHY : selectedOrigin;
        strategy = strategy == null ? "" : strategy;
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        if (valueRedacted) {
            value = REDACTED;
        } else {
            value = value == null ? "" : value;
        }
        if (matchCount < 0) {
            matchCount = 0;
        }
    }

    public static LocatorResolutionEvidence of(
            String stepId,
            LocatorOriginKind requested,
            LocatorOriginKind selected,
            String strategy,
            String value,
            boolean redactValue,
            String fallbackReason,
            int matchCount) {
        return new LocatorResolutionEvidence(
                stepId, requested, selected, strategy, value, redactValue, fallbackReason, matchCount);
    }
}
