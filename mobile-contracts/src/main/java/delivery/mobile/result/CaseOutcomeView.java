package delivery.mobile.result;

/**
 * FE/BE-facing case outcome (M2-07). Mirrors runner {@code CaseOutcome}
 * without depending on mobile-runner.
 */
public enum CaseOutcomeView {
    PASSED,
    FAILED,
    BLOCKED,
    TODO,
    INTERRUPTED_UNCERTAIN,
    EXCLUDED_SIMULATED;

    public static CaseOutcomeView parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TODO;
        }
        return CaseOutcomeView.valueOf(raw.trim().toUpperCase());
    }
}
