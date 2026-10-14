package project.runtime.auth;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import project.runtime.auth.AuthCoordinator.Mode;
import project.runtime.auth.AuthCoordinator.Plan;
import project.runtime.auth.AuthCoordinator.SessionObservation;
import project.utils.dataReader.PropertyReader;

import java.util.Map;

/** Admit/reject without live credentials — AE AuthMode / profile revision ids as refs only. */
public class AuthCoordinatorTest {

    @AfterMethod
    public void reset() {
        PropertyReader.resetForTests();
    }

    @Test
    public void loginUnderTestAlwaysAdmitsWithoutCredentials() {
        Plan p = AuthCoordinator.plan(
                Mode.LOGIN_UNDER_TEST, "rev-1", "", "", "", SessionObservation.UNKNOWN);
        Assert.assertTrue(p.accepted());
        Assert.assertEquals(p.profileRevisionId(), "rev-1");
    }

    @Test
    public void freshLoginRejectsWithoutResetPath() {
        Plan p = AuthCoordinator.plan(
                Mode.FRESH_LOGIN, "rev-2", "login-tc", "", "", SessionObservation.SIGNED_IN);
        Assert.assertFalse(p.accepted());
        Assert.assertEquals(p.failureCode(), "AUTH_RESET_UNCONFIGURED");
    }

    @Test
    public void freshLoginAdmitsWithLogoutRefNoSecretValues() {
        Plan p = AuthCoordinator.plan(
                Mode.FRESH_LOGIN, "rev-3", "login-tc", "logout-tc", "", SessionObservation.SIGNED_IN);
        Assert.assertTrue(p.accepted());
        Assert.assertFalse(p.message().toLowerCase().contains("password"));
        Assert.assertFalse(p.message().toLowerCase().contains("token"));
    }

    @Test
    public void ensureLoggedInRejectsUnknownSession() {
        Plan p = AuthCoordinator.plan(
                Mode.ENSURE_LOGGED_IN, "rev-4", "login-tc", "", "", SessionObservation.UNKNOWN);
        Assert.assertFalse(p.accepted());
        Assert.assertEquals(p.failureCode(), "SESSION_CHECK_UNCERTAIN");
    }

    @Test
    public void applyConfiguredNoOpWhenModeUnset() {
        PropertyReader.loadFromMapForTests(Map.of("mobile.udid", "emulator-5554"));
        AuthCoordinator.applyConfigured(); // must not throw
    }

    @Test
    public void applyConfiguredRejectsEnsureWhenObservationUnknown() {
        PropertyReader.loadFromMapForTests(Map.of(
                AuthCoordinator.PROP_MODE, "ENSURE_LOGGED_IN",
                AuthCoordinator.PROP_PROFILE_REVISION, "rev-x",
                AuthCoordinator.PROP_LOGIN_TC, "TC_LOGIN"));
        try {
            AuthCoordinator.applyConfigured();
            Assert.fail("expected reject");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("SESSION_CHECK_UNCERTAIN"), ex.getMessage());
            Assert.assertFalse(ex.getMessage().contains("password"));
            Assert.assertTrue(ex.getMessage().contains("secret-ref:"), ex.getMessage());
        }
    }

    @Test
    public void modeNamesMatchAeAuthModeContract() {
        // Reference AE AuthMode enum names without importing AE into customer classpath
        for (String name : new String[] {
                "ENSURE_LOGGED_IN", "FRESH_LOGIN", "LOGGED_OUT", "LOGIN_UNDER_TEST"}) {
            Assert.assertEquals(Mode.parse(name).name(), name);
        }
    }
}
