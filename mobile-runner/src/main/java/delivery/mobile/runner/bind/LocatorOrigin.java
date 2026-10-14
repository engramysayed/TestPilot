package delivery.mobile.runner.bind;

public enum LocatorOrigin {
    USER, HIERARCHY, PRECISION, MEMORY;

    public static LocatorOrigin parse(String raw) {
        if (raw == null || raw.isBlank()) return HIERARCHY;
        return LocatorOrigin.valueOf(raw.trim().toUpperCase());
    }
}
