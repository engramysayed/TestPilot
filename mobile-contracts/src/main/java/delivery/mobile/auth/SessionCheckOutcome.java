package delivery.mobile.auth;

/**
 * Session check trichotomy (architecture §7 / M3-02).
 * {@link #UNKNOWN_ERROR} must never be treated as permission to proceed.
 */
public enum SessionCheckOutcome {
    SIGNED_IN_AS_REQUESTED,
    SIGNED_OUT_OR_DIFFERENT_ACCOUNT,
    UNKNOWN_ERROR;

    public boolean mayProceedAsAuthenticated() {
        return this == SIGNED_IN_AS_REQUESTED;
    }
}
