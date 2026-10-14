package delivery.mobile.ir;

public enum MobilePlatform {
    ANDROID;

    public static MobilePlatform parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MobileIrException("PLATFORM_REQUIRED", "platform is required");
        }
        String u = raw.trim().toUpperCase();
        if ("ANDROID".equals(u)) {
            return ANDROID;
        }
        if ("WEB".equals(u)) {
            throw new MobileIrException("PLATFORM_WEB_REFUSED",
                    "mobile IR refuses WEB; use ProvenStepCodec for web");
        }
        throw new MobileIrException("PLATFORM_UNSUPPORTED", "unsupported platform: " + raw);
    }
}
