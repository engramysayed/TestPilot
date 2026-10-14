package delivery.mobile.model;

/** Project platform; immutable after create. Missing/legacy rows backfill to WEB. */
public enum ProjectPlatform {
    WEB,
    ANDROID;

    public static ProjectPlatform parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }
        return ProjectPlatform.valueOf(raw.trim().toUpperCase());
    }
}
