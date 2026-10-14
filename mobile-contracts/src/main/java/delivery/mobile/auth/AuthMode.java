package delivery.mobile.auth;

/** Auth policy modes (architecture §7 / M3-01). */
public enum AuthMode {
    ENSURE_LOGGED_IN,
    FRESH_LOGIN,
    LOGGED_OUT,
    LOGIN_UNDER_TEST;

    public static AuthMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AuthContractException("AUTH_MODE", "auth mode required");
        }
        return AuthMode.valueOf(raw.trim().toUpperCase());
    }
}
