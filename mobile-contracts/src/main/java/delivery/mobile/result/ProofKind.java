package delivery.mobile.result;

/** How the case/step was proven (M2-07). JSON-friendly enum. */
public enum ProofKind {
    NONE,
    HIERARCHY,
    ASSERTION,
    VISION,
    PRECISION,
    USER_LOCATOR,
    SETUP_ONLY,
    UNKNOWN;

    public static ProofKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return ProofKind.valueOf(raw.trim().toUpperCase());
    }
}
