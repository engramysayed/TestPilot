package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.ObservedSessionState;
import delivery.mobile.auth.SuppliedSecretInputs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Configurable fake session port for M3-02 offline tests. Never retains secrets in logs list. */
public final class FakeAuthSessionPort implements AuthSessionPort {
    private Supplier<ObservedSessionState> check = ObservedSessionState::signedOut;
    private Supplier<ObservedSessionState> afterLogout = ObservedSessionState::signedOut;
    private Supplier<ObservedSessionState> afterLogin =
            () -> ObservedSessionState.signedInAsRequested("acct-primary", true);
    private final List<String> actions = new ArrayList<>();
    private int loginCount;
    private int logoutCount;

    public FakeAuthSessionPort withCheck(ObservedSessionState s) {
        this.check = () -> s;
        return this;
    }

    public FakeAuthSessionPort withAfterLogout(ObservedSessionState s) {
        this.afterLogout = () -> s;
        return this;
    }

    public FakeAuthSessionPort withAfterLogin(ObservedSessionState s) {
        this.afterLogin = () -> s;
        return this;
    }

    public List<String> actions() {
        return List.copyOf(actions);
    }

    public int loginCount() {
        return loginCount;
    }

    public int logoutCount() {
        return logoutCount;
    }

    @Override
    public ObservedSessionState checkSession(AuthProfileRevision profile) {
        actions.add("CHECK");
        return check.get();
    }

    @Override
    public void runLogout(String logoutTcId) {
        logoutCount++;
        actions.add("LOGOUT:" + Objects.requireNonNullElse(logoutTcId, ""));
        check = afterLogout;
    }

    @Override
    public void runReset(String setupTcId) {
        actions.add("RESET:" + Objects.requireNonNullElse(setupTcId, ""));
        check = afterLogout;
    }

    @Override
    public void runLogin(String loginTcId, SuppliedSecretInputs secrets) {
        loginCount++;
        // Record key presence only — never secret values
        actions.add("LOGIN:" + Objects.requireNonNullElse(loginTcId, "")
                + ":keys=" + (secrets == null ? "[]" : secrets.keys()));
        check = afterLogin;
    }

    @Override
    public ObservedSessionState verifySignedOut(AuthProfileRevision profile) {
        actions.add("VERIFY_SIGNED_OUT");
        return check.get();
    }

    @Override
    public ObservedSessionState verifySignedIn(AuthProfileRevision profile) {
        actions.add("VERIFY_SIGNED_IN");
        return check.get();
    }
}
