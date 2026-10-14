package delivery.mobile.auth;

/** Planned auth setup phases (M3-02). */
public enum AuthSetupPhase {
    SESSION_CHECK,
    LOGOUT,
    RESET,
    LOGIN,
    VERIFY_SIGNED_OUT,
    VERIFY_SESSION,
    SKIP_AUTOMATIC_AUTH
}
