package delivery.mobile.model;

public enum MobileAppSource {
    APK,
    INSTALLED;

    public static MobileAppSource parse(String raw) {
        return MobileAppSource.valueOf(raw.trim().toUpperCase());
    }
}
