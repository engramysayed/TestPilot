package delivery.mobile.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans auth setup for the four architecture §7 modes (M3-02).
 * Does not execute device actions; unknown session never means proceed.
 */
public final class AuthModePlanner {
    public static final String AUTH_RESET_UNCONFIGURED = "AUTH_RESET_UNCONFIGURED";
    public static final String SESSION_CHECK_UNCERTAIN = "SESSION_CHECK_UNCERTAIN";

    private AuthModePlanner() {
    }

    public static boolean hasSafeResetPath(AuthProfileRevision profile) {
        if (profile == null) {
            return false;
        }
        return (profile.logoutTcId() != null && !profile.logoutTcId().isBlank())
                || (profile.setupTcId() != null && !profile.setupTcId().isBlank());
    }

    public static AuthModePlan plan(
            AuthMode mode, AuthProfileRevision profile, ObservedSessionState current) {
        if (profile == null) {
            return AuthModePlan.reject(
                    mode == null ? AuthMode.ENSURE_LOGGED_IN : mode,
                    "AUTH_PROFILE_MISSING",
                    "auth profile revision required",
                    SessionCheckOutcome.UNKNOWN_ERROR);
        }
        AuthMode m = mode == null ? profile.defaultMode() : mode;
        ObservedSessionState state = current == null
                ? ObservedSessionState.unknown("no session observation")
                : current;

        return switch (m) {
            case LOGIN_UNDER_TEST -> planLoginUnderTest(state);
            case FRESH_LOGIN -> planFreshLogin(profile, state);
            case LOGGED_OUT -> planLoggedOut(profile, state);
            case ENSURE_LOGGED_IN -> planEnsureLoggedIn(profile, state);
        };
    }

    private static AuthModePlan planLoginUnderTest(ObservedSessionState state) {
        // Skip automatic auth entirely — no hidden logout/login prelude.
        List<AuthSetupStep> steps = List.of(new AuthSetupStep(
                AuthSetupPhase.SKIP_AUTOMATIC_AUTH, "", "LOGIN_UNDER_TEST: body owns login"));
        return AuthModePlan.accept(
                AuthMode.LOGIN_UNDER_TEST, steps, true, false, state.outcome());
    }

    private static AuthModePlan planFreshLogin(AuthProfileRevision profile, ObservedSessionState state) {
        if (!hasSafeResetPath(profile)) {
            return AuthModePlan.reject(
                    AuthMode.FRESH_LOGIN,
                    AUTH_RESET_UNCONFIGURED,
                    "FRESH_LOGIN requires configured logout or reset/setup TC",
                    state.outcome());
        }
        List<AuthSetupStep> steps = new ArrayList<>();
        if (profile.logoutTcId() != null && !profile.logoutTcId().isBlank()) {
            steps.add(new AuthSetupStep(AuthSetupPhase.LOGOUT, profile.logoutTcId(), "fresh logout"));
        } else {
            steps.add(new AuthSetupStep(AuthSetupPhase.RESET, profile.setupTcId(), "fresh reset/setup"));
        }
        steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SIGNED_OUT, "", "verify signed out before login"));
        steps.add(new AuthSetupStep(AuthSetupPhase.LOGIN, profile.loginTcId(), "fresh login"));
        steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SESSION, "", "verify session after login"));
        return AuthModePlan.accept(AuthMode.FRESH_LOGIN, steps, false, false, state.outcome());
    }

    private static AuthModePlan planLoggedOut(AuthProfileRevision profile, ObservedSessionState state) {
        if (state.outcome() == SessionCheckOutcome.UNKNOWN_ERROR) {
            return AuthModePlan.reject(
                    AuthMode.LOGGED_OUT, SESSION_CHECK_UNCERTAIN,
                    "LOGGED_OUT: unknown session check cannot proceed", state.outcome());
        }
        List<AuthSetupStep> steps = new ArrayList<>();
        steps.add(new AuthSetupStep(AuthSetupPhase.SESSION_CHECK, "", "observe session"));
        if (state.outcome() == SessionCheckOutcome.SIGNED_IN_AS_REQUESTED
                || state.detail() == SessionDetailCode.WRONG_ACCOUNT) {
            if (!hasSafeResetPath(profile)) {
                return AuthModePlan.reject(
                        AuthMode.LOGGED_OUT, AUTH_RESET_UNCONFIGURED,
                        "LOGGED_OUT needs logout/reset to leave signed-in state", state.outcome());
            }
            if (profile.logoutTcId() != null && !profile.logoutTcId().isBlank()) {
                steps.add(new AuthSetupStep(AuthSetupPhase.LOGOUT, profile.logoutTcId(), "establish signed out"));
            } else {
                steps.add(new AuthSetupStep(AuthSetupPhase.RESET, profile.setupTcId(), "reset to signed out"));
            }
        }
        steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SIGNED_OUT, "", "verify signed out; never login"));
        return AuthModePlan.accept(AuthMode.LOGGED_OUT, steps, false, false, state.outcome());
    }

    private static AuthModePlan planEnsureLoggedIn(AuthProfileRevision profile, ObservedSessionState state) {
        if (state.outcome() == SessionCheckOutcome.UNKNOWN_ERROR) {
            return AuthModePlan.reject(
                    AuthMode.ENSURE_LOGGED_IN, SESSION_CHECK_UNCERTAIN,
                    "ENSURE_LOGGED_IN: unknown session check cannot proceed", state.outcome());
        }
        List<AuthSetupStep> steps = new ArrayList<>();
        steps.add(new AuthSetupStep(AuthSetupPhase.SESSION_CHECK, "", "observe session"));

        if (state.outcome() == SessionCheckOutcome.SIGNED_IN_AS_REQUESTED) {
            steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SESSION, "", "confirm cached session"));
            boolean weaker = !state.strongIdentityEvidence();
            if (weaker) {
                // Weaker identity: perform declared fresh-login sequence rather than claim known account.
                return planEnsureWithLogin(profile, steps, state, true);
            }
            return AuthModePlan.accept(
                    AuthMode.ENSURE_LOGGED_IN, steps, false, false, state.outcome());
        }

        // signed-out / wrong account / expired → logout if wrong account, then login
        if (state.detail() == SessionDetailCode.WRONG_ACCOUNT) {
            if (!hasSafeResetPath(profile)) {
                return AuthModePlan.reject(
                        AuthMode.ENSURE_LOGGED_IN, AUTH_RESET_UNCONFIGURED,
                        "wrong account requires logout/reset path", state.outcome());
            }
            if (profile.logoutTcId() != null && !profile.logoutTcId().isBlank()) {
                steps.add(new AuthSetupStep(AuthSetupPhase.LOGOUT, profile.logoutTcId(), "wrong account logout"));
            } else {
                steps.add(new AuthSetupStep(AuthSetupPhase.RESET, profile.setupTcId(), "wrong account reset"));
            }
            steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SIGNED_OUT, "", "verify signed out after wrong account"));
        }
        return planEnsureWithLogin(profile, steps, state, false);
    }

    private static AuthModePlan planEnsureWithLogin(
            AuthProfileRevision profile,
            List<AuthSetupStep> prefix,
            ObservedSessionState state,
            boolean weaker) {
        List<AuthSetupStep> steps = new ArrayList<>(prefix);
        if (weaker) {
            // Replace simple verify with full login path for unverified identity.
            steps = new ArrayList<>();
            steps.add(new AuthSetupStep(AuthSetupPhase.SESSION_CHECK, "", "observe session"));
            if (hasSafeResetPath(profile)) {
                if (profile.logoutTcId() != null && !profile.logoutTcId().isBlank()) {
                    steps.add(new AuthSetupStep(AuthSetupPhase.LOGOUT, profile.logoutTcId(),
                            "weaker identity → fresh login sequence"));
                } else {
                    steps.add(new AuthSetupStep(AuthSetupPhase.RESET, profile.setupTcId(),
                            "weaker identity → reset then login"));
                }
                steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SIGNED_OUT, "", "verify signed out"));
            }
        }
        if (profile.loginTcId() == null || profile.loginTcId().isBlank()) {
            return AuthModePlan.reject(
                    AuthMode.ENSURE_LOGGED_IN, "AUTH_LOGIN_UNCONFIGURED",
                    "login TC required", state.outcome());
        }
        steps.add(new AuthSetupStep(AuthSetupPhase.LOGIN, profile.loginTcId(), "login when needed"));
        steps.add(new AuthSetupStep(AuthSetupPhase.VERIFY_SESSION, "", "verify session/account"));
        return AuthModePlan.accept(AuthMode.ENSURE_LOGGED_IN, steps, false, weaker, state.outcome());
    }
}
