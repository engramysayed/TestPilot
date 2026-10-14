package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthMode;
import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.LogicalAccountRef;
import delivery.mobile.auth.ObservedSessionState;
import delivery.mobile.auth.SecretInputDeclaration;
import delivery.mobile.auth.SuppliedSecretInputs;
import delivery.mobile.auth.AuthModePlanner;
import delivery.mobile.result.SetupOutcome;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class AuthModeExecutorTest {

    private static final String CANARY = "SECRET_CANARY_OTP_999888";

    private AuthProfileRevision profile(AuthMode mode, String logout, String setup) {
        return new AuthProfileRevision(
                "prof", "rev-1", "t", "p", "TC-LOGIN", "lib", mode,
                List.of("s"), List.of("o"), logout, setup,
                new LogicalAccountRef("acct-primary", "Primary"),
                List.of(SecretInputDeclaration.otp("auth.otp")),
                List.of(), 1L);
    }

    private SuppliedSecretInputs secrets() {
        return SuppliedSecretInputs.of(Map.of("auth.otp", CANARY));
    }

    @Test
    public void validCachedSession_ensure_noLogin() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.signedInAsRequested("acct-primary", true));
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.ENSURE_LOGGED_IN, profile(AuthMode.ENSURE_LOGGED_IN, "TC-LOGOUT", ""), secrets());
        Assert.assertTrue(r.ok());
        Assert.assertEquals(r.setupOutcome(), SetupOutcome.PASSED);
        Assert.assertEquals(port.loginCount(), 0);
        Assert.assertTrue(port.actions().contains("VERIFY_SIGNED_IN"));
    }

    @Test
    public void expiredSession_ensure_logsIn() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.expired())
                .withAfterLogin(ObservedSessionState.signedInAsRequested("acct-primary", true));
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.ENSURE_LOGGED_IN, profile(AuthMode.ENSURE_LOGGED_IN, "TC-LOGOUT", ""), secrets());
        Assert.assertTrue(r.ok(), r.message());
        Assert.assertEquals(port.loginCount(), 1);
        Assert.assertFalse(String.join(",", port.actions()).contains(CANARY));
    }

    @Test
    public void wrongAccount_ensure_logoutThenLogin() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.wrongAccount("acct-other"))
                .withAfterLogout(ObservedSessionState.signedOut())
                .withAfterLogin(ObservedSessionState.signedInAsRequested("acct-primary", true));
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.ENSURE_LOGGED_IN, profile(AuthMode.ENSURE_LOGGED_IN, "TC-LOGOUT", ""), secrets());
        Assert.assertTrue(r.ok(), r.message());
        Assert.assertEquals(port.logoutCount(), 1);
        Assert.assertEquals(port.loginCount(), 1);
        int logoutAt = port.actions().indexOf("LOGOUT:TC-LOGOUT");
        int loginAt = -1;
        for (int i = 0; i < port.actions().size(); i++) {
            if (port.actions().get(i).startsWith("LOGIN:")) {
                loginAt = i;
                break;
            }
        }
        Assert.assertTrue(logoutAt >= 0 && loginAt > logoutAt);
    }

    @Test
    public void freshLogin_executesResetPath() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.signedInAsRequested("acct-primary", true))
                .withAfterLogout(ObservedSessionState.signedOut())
                .withAfterLogin(ObservedSessionState.signedInAsRequested("acct-primary", true));
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.FRESH_LOGIN, profile(AuthMode.FRESH_LOGIN, "TC-LOGOUT", ""), secrets());
        Assert.assertTrue(r.ok(), r.message());
        Assert.assertEquals(port.logoutCount(), 1);
        Assert.assertEquals(port.loginCount(), 1);
    }

    @Test
    public void freshLogin_noResetPath_failsExplicitly() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.signedOut());
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.FRESH_LOGIN, profile(AuthMode.FRESH_LOGIN, "", ""), secrets());
        Assert.assertFalse(r.ok());
        Assert.assertEquals(r.failureCode(), AuthModePlanner.AUTH_RESET_UNCONFIGURED);
        Assert.assertEquals(port.loginCount(), 0);
    }

    @Test
    public void unknownSession_doesNotProceed() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.unknown("adb blip"));
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.ENSURE_LOGGED_IN, profile(AuthMode.ENSURE_LOGGED_IN, "TC-LOGOUT", ""), secrets());
        Assert.assertFalse(r.ok());
        Assert.assertEquals(r.failureCode(), AuthModePlanner.SESSION_CHECK_UNCERTAIN);
        Assert.assertEquals(port.loginCount(), 0);
    }

    @Test
    public void loginUnderTest_noHiddenPrelude() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.signedOut());
        AuthSetupExecutionResult r = new AuthModeExecutor(port).execute(
                AuthMode.LOGIN_UNDER_TEST, profile(AuthMode.LOGIN_UNDER_TEST, "TC-LOGOUT", ""), secrets());
        Assert.assertTrue(r.ok());
        Assert.assertTrue(r.automaticAuthSkipped());
        Assert.assertEquals(r.setupOutcome(), SetupOutcome.SKIPPED);
        Assert.assertEquals(port.loginCount(), 0);
        Assert.assertEquals(port.logoutCount(), 0);
        Assert.assertFalse(port.actions().contains("CHECK"),
                "LOGIN_UNDER_TEST must not run hidden session prelude: " + port.actions());
    }

    @Test
    public void secretCanaryAbsentFromActionLog() {
        FakeAuthSessionPort port = new FakeAuthSessionPort()
                .withCheck(ObservedSessionState.expired())
                .withAfterLogin(ObservedSessionState.signedInAsRequested("acct-primary", true));
        new AuthModeExecutor(port).execute(
                AuthMode.ENSURE_LOGGED_IN, profile(AuthMode.ENSURE_LOGGED_IN, "TC-LOGOUT", ""), secrets());
        for (String a : port.actions()) {
            Assert.assertFalse(a.contains(CANARY), a);
        }
        Assert.assertFalse(port.actions().toString().contains(CANARY));
    }
}
