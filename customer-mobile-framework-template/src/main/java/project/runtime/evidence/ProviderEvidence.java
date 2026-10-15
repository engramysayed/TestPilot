package project.runtime.evidence;

/**
 * Honest provider label for a generated step.
 * A missing model or a Keel fallback is not a pass and is not labeled as Cursor.
 */
public final class ProviderEvidence {
    private ProviderEvidence() {}

    public static Result missing(String allowedProvider) {
        return new Result(allowedProvider == null ? "" : allowedProvider, "NONE", "MISSING_PROVIDER", "", false, false);
    }

    public static Result fallback(String allowedProvider, String reason) {
        return new Result(allowedProvider == null ? "" : allowedProvider, "keel", reason == null ? "" : reason, "", false, false);
    }

    public static Result used(String provider, String model) {
        if (provider == null || provider.isBlank() || "NONE".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("missing provider is not PASS");
        }
        boolean cursor = "cursor".equalsIgnoreCase(provider);
        return new Result(provider, provider, "", model == null ? "" : model, true, cursor);
    }

    public record Result(
            String allowedProvider,
            String usedProvider,
            String fallbackReason,
            String visionModel,
            boolean passed,
            boolean countsAsCursor
    ) {}
}
