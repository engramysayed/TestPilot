package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Planned auth preparation for a mode (M3-02). Failure codes include
 * {@code AUTH_RESET_UNCONFIGURED} and {@code SESSION_CHECK_UNCERTAIN}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthModePlan(
        AuthMode mode,
        boolean accepted,
        String failureCode,
        String message,
        List<AuthSetupStep> steps,
        boolean automaticAuthSkipped,
        boolean weakerIdentityEvidence,
        SessionCheckOutcome initialOutcome
) {
    public AuthModePlan {
        mode = mode == null ? AuthMode.ENSURE_LOGGED_IN : mode;
        failureCode = failureCode == null ? "" : failureCode;
        message = message == null ? "" : message;
        steps = steps == null ? List.of() : List.copyOf(steps);
        initialOutcome = initialOutcome == null ? SessionCheckOutcome.UNKNOWN_ERROR : initialOutcome;
    }

    public static AuthModePlan reject(
            AuthMode mode, String code, String message, SessionCheckOutcome initial) {
        return new AuthModePlan(mode, false, code, message, List.of(), false, false, initial);
    }

    public static AuthModePlan accept(
            AuthMode mode,
            List<AuthSetupStep> steps,
            boolean skipped,
            boolean weaker,
            SessionCheckOutcome initial) {
        return new AuthModePlan(mode, true, "", "", steps, skipped, weaker, initial);
    }
}
