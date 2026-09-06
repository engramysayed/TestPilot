package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoginStepDetectorTest {
    @Test
    public void detectsExplicitLoginSteps() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "Login", "",
                "1. Enter username\n2. Enter password\n3. Click login button",
                "Home shown", "P1", "");
        Assert.assertTrue(LoginStepDetector.hasLoginSteps(tc));
    }

    @Test
    public void ignoresCatalogOnlyWordingForHasLoginSteps() {
        ManualTestCase tc = new ManualTestCase(
                "TC2", "See catalog", "Already logged in",
                "1. Confirm catalog page is shown",
                "Products visible", "P1", "");
        Assert.assertFalse(LoginStepDetector.hasLoginSteps(tc));
    }

    @Test
    public void needsAuthWhenPreconditionsRequireSession() {
        ManualTestCase tc = new ManualTestCase(
                "TC2", "See catalog", "Already logged in",
                "1. Confirm catalog page is shown",
                "Products visible", "P1", "");
        Assert.assertTrue(LoginStepDetector.needsAuthenticatedSession(tc, true));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, false));
    }

    @Test
    public void publicPagesDoNotForceLoginPrelude() {
        ManualTestCase tc = new ManualTestCase(
                "TC_TI_02", "Add element", "User is on Add/Remove Elements page",
                "1. Click the Add Element button\n2. Confirm a Delete button appears",
                "Delete appears", "P1", "");
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true));
    }

    @Test
    public void excelLoginStepsAuthorAsBodyNotPrelude() {
        ManualTestCase tc = new ManualTestCase(
                "TC_TI_01", "Login", "User is on the Login Page",
                "1. Enter the username test_user\n2. Enter the password TestPass1!\n3. Click the Login button",
                "Secure Area shown", "P1", "");
        Assert.assertTrue(LoginStepDetector.hasLoginSteps(tc));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true),
                "Login TCs must not run login prelude (would strip body steps)");
    }

    @Test
    public void liveLoginFormMeansAuthNeededEvenWithoutExcelLoginWording() {
        ManualTestCase tc = new ManualTestCase(
                "TC2", "See catalog", "",
                "1. Confirm catalog page is shown",
                "Products visible", "P1", "");
        Assert.assertTrue(LoginStepDetector.needsAuthenticatedSession(tc, true, true));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true, false));
    }

    @Test
    public void loginFailureDoesNotNeedAuthSession() {
        ManualTestCase tc = new ManualTestCase(
                "TC3", "Bad password", "",
                "1. Enter username\n2. Enter wrong password\n3. Click login",
                "Error message for invalid password", "P1", "");
        Assert.assertTrue(LoginStepDetector.isLoginFailureCase(tc));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true, true));
    }

    @Test
    public void enteringPasswordOnAPublicFormIsNotALoginCase() {
        ManualTestCase tc = new ManualTestCase(
                "TC_REG",
                "Create account",
                "No login required.",
                """
                        1. Enter Maya in the First name field
                        2. Enter Maya@example.com in the Email field
                        3. Enter TestPass!23456 in the Password field
                        4. Click the Submit button
                        """,
                "Account form submitted",
                "P1",
                "");
        Assert.assertFalse(LoginStepDetector.hasLoginSteps(tc));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true, false));
    }

    @Test
    public void noLoginRequiredInPreconditions_doesNotForceAuth() {
        ManualTestCase tc = new ManualTestCase(
                "TC_TI_03", "Add element", "No login required.",
                "1. Open the Add/Remove Elements page at /add_remove_elements/\n"
                        + "2. Click the Add Element button",
                "Delete appears", "P1", "");
        Assert.assertFalse(LoginStepDetector.hasLoginSteps(tc));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, true, false));
    }

    @Test
    public void loginRequiredInPreconditions_needsAuthWhenJobHasCreds() {
        ManualTestCase tc = new ManualTestCase(
                "TC_AUTH", "See dashboard", "Login required. Use project credentials.",
                "1. Confirm the text Dashboard is visible",
                "Dashboard shown", "P1", "");
        Assert.assertFalse(LoginStepDetector.hasLoginSteps(tc));
        Assert.assertTrue(LoginStepDetector.needsAuthenticatedSession(tc, true));
        Assert.assertFalse(LoginStepDetector.needsAuthenticatedSession(tc, false));
    }
}
