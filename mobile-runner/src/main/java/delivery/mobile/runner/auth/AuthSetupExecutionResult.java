package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthMode;
import delivery.mobile.auth.AuthModePlan;
import delivery.mobile.auth.ObservedSessionState;
import delivery.mobile.auth.SessionCheckOutcome;
import delivery.mobile.result.SetupOutcome;

import java.util.ArrayList;
import java.util.List;

/** Result of executing an auth mode plan (M3-02). */
public record AuthSetupExecutionResult(
        boolean ok,
        String failureCode,
        String message,
        AuthMode mode,
        SetupOutcome setupOutcome,
        AuthModePlan plan,
        ObservedSessionState finalSession,
        List<String> executedPhases,
        boolean automaticAuthSkipped,
        boolean weakerIdentityEvidence
) {
    public AuthSetupExecutionResult {
        failureCode = failureCode == null ? "" : failureCode;
        message = message == null ? "" : message;
        executedPhases = executedPhases == null ? List.of() : List.copyOf(executedPhases);
        setupOutcome = setupOutcome == null ? SetupOutcome.UNKNOWN : setupOutcome;
    }

    public static AuthSetupExecutionResult fromPlanReject(AuthModePlan plan) {
        return new AuthSetupExecutionResult(
                false, plan.failureCode(), plan.message(), plan.mode(),
                SetupOutcome.BLOCKED, plan, null, List.of(),
                plan.automaticAuthSkipped(), plan.weakerIdentityEvidence());
    }

    public static Builder builder(AuthModePlan plan) {
        return new Builder(plan);
    }

    public static final class Builder {
        private final AuthModePlan plan;
        private final List<String> phases = new ArrayList<>();
        private boolean ok = true;
        private String failureCode = "";
        private String message = "";
        private ObservedSessionState finalSession;
        private SetupOutcome setupOutcome = SetupOutcome.PASSED;

        private Builder(AuthModePlan plan) {
            this.plan = plan;
        }

        public Builder phase(String name) {
            phases.add(name);
            return this;
        }

        public Builder finalSession(ObservedSessionState s) {
            this.finalSession = s;
            return this;
        }

        public Builder fail(String code, String msg) {
            this.ok = false;
            this.failureCode = code;
            this.message = msg;
            this.setupOutcome = SetupOutcome.FAILED;
            return this;
        }

        public AuthSetupExecutionResult build() {
            if (ok && plan.automaticAuthSkipped()) {
                setupOutcome = SetupOutcome.SKIPPED;
            }
            if (!ok && setupOutcome == SetupOutcome.PASSED) {
                setupOutcome = SetupOutcome.FAILED;
            }
            return new AuthSetupExecutionResult(
                    ok, failureCode, message, plan.mode(), setupOutcome, plan,
                    finalSession, phases, plan.automaticAuthSkipped(), plan.weakerIdentityEvidence());
        }
    }

    public boolean finalSessionOkForMode() {
        if (automaticAuthSkipped) {
            return true;
        }
        if (finalSession == null) {
            return false;
        }
        if (mode == AuthMode.LOGGED_OUT) {
            return finalSession.outcome() == SessionCheckOutcome.SIGNED_OUT_OR_DIFFERENT_ACCOUNT
                    && finalSession.detail() != delivery.mobile.auth.SessionDetailCode.WRONG_ACCOUNT;
        }
        if (mode == AuthMode.ENSURE_LOGGED_IN || mode == AuthMode.FRESH_LOGIN) {
            return finalSession.outcome() == SessionCheckOutcome.SIGNED_IN_AS_REQUESTED;
        }
        return ok;
    }
}
