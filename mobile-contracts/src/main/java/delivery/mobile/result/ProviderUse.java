package delivery.mobile.result;

/**
 * What a mobile step actually used. A missing or fallback provider is never a pass,
 * and a volatile WebView handle is never stored.
 */
public record ProviderUse(
        String contextKind,
        String contextPredicate,
        String allowedProvider,
        String usedProvider,
        String fallbackReason,
        String visionModel,
        boolean passed
) {
    public ProviderUse {
        contextKind = contextKind == null ? "" : contextKind;
        contextPredicate = contextPredicate == null ? "" : contextPredicate;
        allowedProvider = allowedProvider == null ? "" : allowedProvider;
        usedProvider = usedProvider == null ? "" : usedProvider;
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        visionModel = visionModel == null ? "" : visionModel;
        if (contextPredicate.contains("WEBVIEW_")) {
            throw new IllegalArgumentException("volatile WEBVIEW_ handle must not be stored");
        }
        if (passed && (usedProvider.isBlank() || "NONE".equalsIgnoreCase(usedProvider))) {
            throw new IllegalArgumentException("missing provider is not PASS");
        }
    }

    public static ProviderUse missing(String contextKind, String contextPredicate, String allowedProvider) {
        return new ProviderUse(contextKind, contextPredicate, allowedProvider, "NONE", "MISSING_PROVIDER", "", false);
    }

    public static ProviderUse fallback(String contextKind, String contextPredicate, String allowedProvider, String reason) {
        return new ProviderUse(contextKind, contextPredicate, allowedProvider, "keel", reason, "", false);
    }

    public boolean countsAsCursor() {
        return passed && "cursor".equalsIgnoreCase(usedProvider) && fallbackReason.isBlank();
    }
}
