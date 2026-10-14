package delivery.job;

/**
 * B21: structured login failure from {@link JobLoginService}. Code is always {@value #CODE}; {@link #reason()}
 * says why. Still an IllegalStateException whose message starts with "LOGIN_FAILED: ", so existing
 * message-based callers (ProvePhase, preflight, hunt) behave exactly as before.
 */
public class LoginFailedException extends IllegalStateException {
    public static final String CODE = "LOGIN_FAILED";

    public enum Reason {
        /** Username set but no password. */
        MISSING_PASSWORD,
        /** Still on the login form after submitting (wrong credentials or selectors). */
        STILL_ON_LOGIN_FORM,
        /** The login form or the post-login page didn't appear in time. */
        TIMEOUT,
        /** Any other browser/driver error during login. */
        DRIVER_ERROR
    }

    private final Reason reason;

    public LoginFailedException(Reason reason, String detail, Throwable cause) {
        super(CODE + ": " + (detail == null ? "" : detail), cause);
        this.reason = reason;
    }

    public String code() {
        return CODE;
    }

    public Reason reason() {
        return reason;
    }

    /** First LoginFailedException in the cause chain, or null. */
    public static LoginFailedException find(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof LoginFailedException l) {
                return l;
            }
        }
        return null;
    }
}
