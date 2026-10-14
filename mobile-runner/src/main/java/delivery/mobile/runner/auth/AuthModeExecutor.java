package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthMode;
import delivery.mobile.auth.AuthModePlan;
import delivery.mobile.auth.AuthModePlanner;
import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.AuthSetupPhase;
import delivery.mobile.auth.AuthSetupStep;
import delivery.mobile.auth.ObservedSessionState;
import delivery.mobile.auth.SessionCheckOutcome;
import delivery.mobile.auth.SessionDetailCode;
import delivery.mobile.auth.SuppliedSecretInputs;

/**
 * Executes auth mode plans against an {@link AuthSessionPort} (M3-02).
 * LOGIN_UNDER_TEST performs no hidden prelude. Secrets are never logged.
 */
public final class AuthModeExecutor {
    private final AuthSessionPort port;

    public AuthModeExecutor(AuthSessionPort port) {
        this.port = port;
    }

    public AuthSetupExecutionResult execute(
            AuthMode mode, AuthProfileRevision profile, SuppliedSecretInputs secrets) {
        SuppliedSecretInputs supplied = secrets == null ? SuppliedSecretInputs.empty() : secrets;

        // LOGIN_UNDER_TEST: do not even require a session probe to drive setup — skip automatic auth.
        if (mode == AuthMode.LOGIN_UNDER_TEST
                || (mode == null && profile != null && profile.defaultMode() == AuthMode.LOGIN_UNDER_TEST)) {
            AuthModePlan plan = AuthModePlanner.plan(AuthMode.LOGIN_UNDER_TEST, profile,
                    ObservedSessionState.unknown("skipped-probe"));
            // Re-plan with neutral state — planner ignores current for LUT
            plan = AuthModePlanner.plan(AuthMode.LOGIN_UNDER_TEST, profile, ObservedSessionState.signedOut());
            AuthSetupExecutionResult.Builder b = AuthSetupExecutionResult.builder(plan);
            b.phase(AuthSetupPhase.SKIP_AUTOMATIC_AUTH.name());
            return b.build();
        }

        ObservedSessionState current = port.checkSession(profile);
        AuthModePlan plan = AuthModePlanner.plan(mode, profile, current);
        if (!plan.accepted()) {
            return AuthSetupExecutionResult.fromPlanReject(plan);
        }

        AuthSetupExecutionResult.Builder b = AuthSetupExecutionResult.builder(plan);
        ObservedSessionState last = current;
        try {
            for (AuthSetupStep step : plan.steps()) {
                b.phase(step.phase().name());
                switch (step.phase()) {
                    case SESSION_CHECK -> {
                        // already probed
                    }
                    case LOGOUT -> port.runLogout(step.tcRef());
                    case RESET -> port.runReset(step.tcRef());
                    case LOGIN -> port.runLogin(step.tcRef(), supplied);
                    case VERIFY_SIGNED_OUT -> {
                        last = port.verifySignedOut(profile);
                        if (last.outcome() == SessionCheckOutcome.UNKNOWN_ERROR) {
                            return b.finalSession(last)
                                    .fail(AuthModePlanner.SESSION_CHECK_UNCERTAIN, last.message())
                                    .build();
                        }
                        if (last.outcome() == SessionCheckOutcome.SIGNED_IN_AS_REQUESTED
                                || last.detail() == SessionDetailCode.WRONG_ACCOUNT) {
                            return b.finalSession(last)
                                    .fail("AUTH_FAILED", "still signed in after logout/reset")
                                    .build();
                        }
                    }
                    case VERIFY_SESSION -> {
                        last = port.verifySignedIn(profile);
                        if (last.outcome() == SessionCheckOutcome.UNKNOWN_ERROR) {
                            return b.finalSession(last)
                                    .fail(AuthModePlanner.SESSION_CHECK_UNCERTAIN, last.message())
                                    .build();
                        }
                        if (last.outcome() != SessionCheckOutcome.SIGNED_IN_AS_REQUESTED) {
                            return b.finalSession(last)
                                    .fail("AUTH_FAILED", "session verify failed: " + last.detail())
                                    .build();
                        }
                    }
                    case SKIP_AUTOMATIC_AUTH -> {
                        // no-op
                    }
                }
            }
        } catch (RuntimeException ex) {
            return b.finalSession(last)
                    .fail("AUTH_FAILED", "auth setup error")
                    .build();
        }
        return b.finalSession(last).build();
    }
}
