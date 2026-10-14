package delivery.mobile.auth;

/** Detail under a {@link SessionCheckOutcome} (M3-02). */
public enum SessionDetailCode {
    CACHED_VALID,
    EXPIRED,
    SIGNED_OUT,
    WRONG_ACCOUNT,
    IDENTITY_UNVERIFIED,
    PROBE_ERROR,
    NONE;

    public static SessionDetailCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return SessionDetailCode.valueOf(raw.trim().toUpperCase());
    }
}
