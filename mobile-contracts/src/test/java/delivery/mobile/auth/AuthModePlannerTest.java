package delivery.mobile.auth;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class AuthModePlannerTest {

    private static final String CANARY = "SECRET_CANARY_OTP_999888";

    private AuthProfileRevision profile(String logout, String setup) {
        return new AuthProfileRevision(
                "prof", "rev-1", "t", "p", "TC-LOGIN", "lib",
                AuthMode.ENSURE_LOGGED_IN,
                List.of("session:home"), List.of("signedOut:login"),
                logout, setup,
                new LogicalAccountRef("acct-primary", "Primary"),
                List.of(SecretInputDeclaration.otp("auth.otp")),
                List.of(), 1L);
    }

    @Test
    public void validCachedSession_ensureLoggedIn_verifiesOnly() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.ENSURE_LOGGED_IN,
                profile("TC-LOGOUT", ""),
                ObservedSessionState.signedInAsRequested("acct-primary", true));
        Assert.assertTrue(plan.accepted());
        Assert.assertFalse(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.LOGIN));
        Assert.assertTrue(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.VERIFY_SESSION));
        Assert.assertFalse(plan.weakerIdentityEvidence());
    }

    @Test
    public void expiredSession_ensureLoggedIn_plansLogin() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.ENSURE_LOGGED_IN,
                profile("TC-LOGOUT", ""),
                ObservedSessionState.expired());
        Assert.assertTrue(plan.accepted());
        Assert.assertTrue(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.LOGIN));
        Assert.assertEquals(plan.steps().stream()
                .filter(s -> s.phase() == AuthSetupPhase.LOGIN).findFirst().orElseThrow().tcRef(), "TC-LOGIN");
    }

    @Test
    public void wrongAccount_ensureLoggedIn_logoutThenLogin() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.ENSURE_LOGGED_IN,
                profile("TC-LOGOUT", ""),
                ObservedSessionState.wrongAccount("acct-other"));
        Assert.assertTrue(plan.accepted());
        List<AuthSetupPhase> phases = plan.steps().stream().map(AuthSetupStep::phase).toList();
        Assert.assertTrue(phases.contains(AuthSetupPhase.LOGOUT));
        Assert.assertTrue(phases.contains(AuthSetupPhase.VERIFY_SIGNED_OUT));
        Assert.assertTrue(phases.contains(AuthSetupPhase.LOGIN));
        Assert.assertTrue(phases.contains(AuthSetupPhase.VERIFY_SESSION));
        int logoutAt = phases.indexOf(AuthSetupPhase.LOGOUT);
        int loginAt = phases.indexOf(AuthSetupPhase.LOGIN);
        Assert.assertTrue(logoutAt < loginAt);
    }

    @Test
    public void freshLogin_withLogoutPath_alwaysResets() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.FRESH_LOGIN,
                profile("TC-LOGOUT", ""),
                ObservedSessionState.signedInAsRequested("acct-primary", true));
        Assert.assertTrue(plan.accepted());
        List<AuthSetupPhase> phases = plan.steps().stream().map(AuthSetupStep::phase).toList();
        Assert.assertEquals(phases.get(0), AuthSetupPhase.LOGOUT);
        Assert.assertTrue(phases.contains(AuthSetupPhase.VERIFY_SIGNED_OUT));
        Assert.assertTrue(phases.contains(AuthSetupPhase.LOGIN));
    }

    @Test
    public void freshLogin_noLogoutOrReset_AUTH_RESET_UNCONFIGURED() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.FRESH_LOGIN,
                profile("", ""),
                ObservedSessionState.signedOut());
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), AuthModePlanner.AUTH_RESET_UNCONFIGURED);
    }

    @Test
    public void unknownSessionCheck_ensureAndLoggedOut_reject() {
        ObservedSessionState unk = ObservedSessionState.unknown("probe failed");
        AuthModePlan ensure = AuthModePlanner.plan(AuthMode.ENSURE_LOGGED_IN, profile("TC-LOGOUT", ""), unk);
        Assert.assertFalse(ensure.accepted());
        Assert.assertEquals(ensure.failureCode(), AuthModePlanner.SESSION_CHECK_UNCERTAIN);

        AuthModePlan loggedOut = AuthModePlanner.plan(AuthMode.LOGGED_OUT, profile("TC-LOGOUT", ""), unk);
        Assert.assertFalse(loggedOut.accepted());
        Assert.assertEquals(loggedOut.failureCode(), AuthModePlanner.SESSION_CHECK_UNCERTAIN);
    }

    @Test
    public void loginUnderTest_skipsAutomaticAuth_noHiddenPrelude() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.LOGIN_UNDER_TEST,
                profile("TC-LOGOUT", ""),
                ObservedSessionState.signedInAsRequested("acct-primary", true));
        Assert.assertTrue(plan.accepted());
        Assert.assertTrue(plan.automaticAuthSkipped());
        Assert.assertFalse(plan.steps().stream().anyMatch(s ->
                s.phase() == AuthSetupPhase.LOGIN
                        || s.phase() == AuthSetupPhase.LOGOUT
                        || s.phase() == AuthSetupPhase.RESET));
        Assert.assertTrue(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.SKIP_AUTOMATIC_AUTH));
    }

    @Test
    public void loggedOut_fromSignedIn_logoutNeverLogin() {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.LOGGED_OUT,
                profile("TC-LOGOUT", ""),
                ObservedSessionState.signedInAsRequested("acct-primary", true));
        Assert.assertTrue(plan.accepted());
        Assert.assertTrue(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.LOGOUT));
        Assert.assertTrue(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.VERIFY_SIGNED_OUT));
        Assert.assertFalse(plan.steps().stream().anyMatch(s -> s.phase() == AuthSetupPhase.LOGIN));
    }

    @Test
    public void accountPredicates_wrongAccountAndUnknown() {
        LogicalAccountRef want = new LogicalAccountRef("acct-primary", "P");
        ObservedSessionState wrong = AccountPredicates.evaluate(want, true, false, "acct-other", false);
        Assert.assertEquals(wrong.outcome(), SessionCheckOutcome.SIGNED_OUT_OR_DIFFERENT_ACCOUNT);
        Assert.assertEquals(wrong.detail(), SessionDetailCode.WRONG_ACCOUNT);

        ObservedSessionState unk = AccountPredicates.evaluate(want, false, false, "", true);
        Assert.assertEquals(unk.outcome(), SessionCheckOutcome.UNKNOWN_ERROR);

        ObservedSessionState weak = AccountPredicates.evaluate(want, true, false, "", false);
        Assert.assertEquals(weak.outcome(), SessionCheckOutcome.SIGNED_IN_AS_REQUESTED);
        Assert.assertFalse(weak.strongIdentityEvidence());

        ObservedSessionState ok = AccountPredicates.evaluate(want, true, false, "acct-primary", false);
        Assert.assertTrue(ok.strongIdentityEvidence());
        Assert.assertEquals(ok.detail(), SessionDetailCode.CACHED_VALID);
    }

    @Test
    public void planJsonHasNoSecretCanary() throws Exception {
        AuthModePlan plan = AuthModePlanner.plan(
                AuthMode.FRESH_LOGIN, profile("TC-LOGOUT", ""), ObservedSessionState.signedOut());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(plan);
        Assert.assertFalse(json.contains(CANARY));
        Assert.assertFalse(plan.toString().contains(CANARY));
    }
}
