package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.ObservedSessionState;
import delivery.mobile.auth.SuppliedSecretInputs;

/**
 * Device/session port for auth mode execution (M3-02). Implementations must not log secret values.
 */
public interface AuthSessionPort {
    ObservedSessionState checkSession(AuthProfileRevision profile);

    void runLogout(String logoutTcId);

    void runReset(String setupTcId);

    /** Login using supplied secrets; implementations must not log raw secret values. */
    void runLogin(String loginTcId, SuppliedSecretInputs secrets);

    ObservedSessionState verifySignedOut(AuthProfileRevision profile);

    ObservedSessionState verifySignedIn(AuthProfileRevision profile);
}
