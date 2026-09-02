package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Negative login cases must keep TYPE_USER / TYPE_PASS / CLICK_LOGIN in the body intents.
 */
public class NegativeLoginIntentRetentionTest {

    @Test
    public void negativeLoginKeepsCredentialIntents() {
        ManualTestCase tc = new ManualTestCase(
                "TC_PTA_02",
                "Login fails with invalid password",
                "This is a negative login case.",
                """
                        1. Open the practice test login page at /practice-test-login/
                        2. Enter the username student
                        3. Enter the password wrongPassword
                        4. Click the Submit button
                        5. Confirm the text Your password is invalid is visible
                        """,
                "Error message Your password is invalid is shown",
                "P1",
                "negative");
        Assert.assertTrue(LoginStepDetector.isLoginFailureCase(tc));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true, true));

        List<StepIntentBinder.IntentLine> retained = StepIntentBinder.bodyIntents(tc, false);

        Assert.assertTrue(retained.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_USER),
                "must keep username type");
        Assert.assertTrue(retained.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_PASS),
                "must keep password type");
        Assert.assertTrue(retained.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.CLICK_LOGIN
                        || i.kind() == StepIntentBinder.IntentKind.CLICK),
                "must keep submit/login click");
        Assert.assertTrue(retained.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE),
                "must keep assert");
    }

    @Test
    public void publicFormKeepsPasswordAndSubmitWhenPreludeDidNotRun() {
        ManualTestCase tc = new ManualTestCase(
                "TC_REG_01",
                "Create account",
                "No login required. Public registration page.",
                """
                        1. Enter Ahmed in the First name field
                        2. Enter TestPass!23456 in the Password field
                        3. Click the Submit button
                        """,
                "Form accepted",
                "P1",
                "");
        Assert.assertFalse(LoginStepDetector.hasLoginSteps(tc),
                "a registration password is not a login case");
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true));

        List<StepIntentBinder.IntentLine> body = StepIntentBinder.bodyIntents(tc, false);
        Assert.assertTrue(body.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_PASS),
                "public forms must still type the password, got " + kinds(body));
        Assert.assertTrue(body.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.CLICK_LOGIN
                        || i.kind() == StepIntentBinder.IntentKind.CLICK),
                "public forms must still click submit, got " + kinds(body));
    }

    @Test
    public void loginPreludeStripsCredentialIntentsFromTheBody() {
        ManualTestCase tc = new ManualTestCase(
                "TC_AUTH",
                "Login",
                "User is on the Login Page",
                """
                        1. Enter the username test_user
                        2. Enter the password TestPass1!
                        3. Click the Login button
                        4. Confirm the text Dashboard is visible
                        """,
                "Dashboard shown",
                "P1",
                "");
        List<StepIntentBinder.IntentLine> body = StepIntentBinder.bodyIntents(tc, true);
        Assert.assertTrue(body.stream().noneMatch(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_USER
                        || i.kind() == StepIntentBinder.IntentKind.TYPE_PASS
                        || i.kind() == StepIntentBinder.IntentKind.CLICK_LOGIN),
                "prelude already typed credentials, got " + kinds(body));
        Assert.assertTrue(body.stream().anyMatch(i -> i.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE));
    }

    private static String kinds(List<StepIntentBinder.IntentLine> body) {
        return body.stream().map(i -> i.kind() + ":" + i.text()).toList().toString();
    }
}
