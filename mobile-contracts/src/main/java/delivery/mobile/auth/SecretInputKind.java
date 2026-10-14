package delivery.mobile.auth;

/** Declared secret input kinds on an auth profile (M3-01). Values are never stored on the profile. */
public enum SecretInputKind {
    PASSWORD,
    OTP,
    PIN,
    OTHER;

    public boolean requiresPerRunSupply() {
        return this == OTP || this == PIN;
    }

    public static SecretInputKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        return SecretInputKind.valueOf(raw.trim().toUpperCase());
    }
}
