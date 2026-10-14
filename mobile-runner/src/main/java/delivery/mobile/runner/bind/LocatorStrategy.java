package delivery.mobile.runner.bind;

/** Native-app locator strategies. CSS is never accepted in NATIVE_APP. */
public enum LocatorStrategy {
    ACCESSIBILITY_ID("accessibilityId"),
    RESOURCE_ID("resourceId"),
    TEXT("text"),
    CLASS_NAME("className"),
    LABEL_SIBLING("labelSibling"),
    XPATH("xpath"),
    CSS("css");

    private final String wire;

    LocatorStrategy(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static LocatorStrategy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BindException(BindException.Code.INVALID_STRATEGY, "strategy required");
        }
        String s = raw.trim();
        for (LocatorStrategy v : values()) {
            if (v.wire.equalsIgnoreCase(s) || v.name().equalsIgnoreCase(s)) {
                return v;
            }
        }
        // common aliases
        if ("id".equalsIgnoreCase(s)) return RESOURCE_ID;
        if ("accessibility".equalsIgnoreCase(s) || "accessibility_id".equalsIgnoreCase(s)) return ACCESSIBILITY_ID;
        if ("label-sibling".equalsIgnoreCase(s) || "label_sibling".equalsIgnoreCase(s)) return LABEL_SIBLING;
        throw new BindException(BindException.Code.INVALID_STRATEGY, "unknown strategy: " + raw);
    }

    public boolean nativeAllowed() {
        return this != CSS;
    }
}
