package delivery.job;

import java.util.Locale;

/** Suggested failure taxonomy. User corrections are stored separately from the suggestion. */
public final class FailureClassifier {
    public enum Kind { ASSERTION, LOCATOR, PROVIDER, INFRASTRUCTURE }

    public record Classification(Kind suggested, Kind effective, boolean userCorrected) {
    }

    private FailureClassifier() {
    }

    public static Kind suggest(String raw) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (lower.contains("assert") || lower.contains("expected") || lower.contains("visual")) {
            return Kind.ASSERTION;
        }
        if (lower.contains("locator") || lower.contains("xpath") || lower.contains("no such element")
                || lower.contains("heal_exhausted") || lower.contains("ambiguous:")
                || lower.contains("no dom candidate")) {
            return Kind.LOCATOR;
        }
        if (lower.contains("ollama") || lower.contains("provider") || lower.contains("allowlist")
                || lower.contains("cursor") || lower.contains("rate limit") || lower.contains("timeout waiting for model")) {
            return Kind.PROVIDER;
        }
        if (lower.contains("chrome") || lower.contains("dns") || lower.contains("connection")
                || lower.contains("lease") || lower.contains("interrupted") || lower.contains("devtools")
                || lower.contains("session id")) {
            return Kind.INFRASTRUCTURE;
        }
        return Kind.INFRASTRUCTURE;
    }

    public static Classification apply(String raw, Kind userCorrection) {
        Kind suggested = suggest(raw);
        if (userCorrection == null) {
            return new Classification(suggested, suggested, false);
        }
        return new Classification(suggested, userCorrection, userCorrection != suggested);
    }
}
