package delivery.mobile.result;

/** Actual locator/heal provider used for a step (M2-07). */
public enum ProviderKind {
    KEEL,
    PRECISION,
    VISION,
    USER,
    MEMORY,
    NONE;

    public static ProviderKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return ProviderKind.valueOf(raw.trim().toUpperCase());
    }
}
