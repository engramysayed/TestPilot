package delivery.mobile.result;

/** Where the observed app build came from (M2-07). */
public enum AppIdentitySource {
    APK,
    INSTALLED,
    UNKNOWN;

    public static AppIdentitySource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return AppIdentitySource.valueOf(raw.trim().toUpperCase());
    }
}
