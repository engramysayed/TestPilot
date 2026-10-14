package delivery.mobile.result;

/**
 * Locator origin for FE-facing evidence (M2-07).
 * Mirrors runner LocatorOrigin without a runner dependency.
 */
public enum LocatorOriginKind {
    USER,
    HIERARCHY,
    PRECISION,
    MEMORY;

    public static LocatorOriginKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HIERARCHY;
        }
        return LocatorOriginKind.valueOf(raw.trim().toUpperCase());
    }
}
