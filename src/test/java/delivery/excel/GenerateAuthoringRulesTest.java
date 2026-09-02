package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class GenerateAuthoringRulesTest {

    private static List<String> check(ManualTestCase tc) {
        return check(tc, null);
    }

    private static List<String> check(ManualTestCase tc, String baseUrl) {
        List<String> errors = new ArrayList<>();
        GenerateAuthoringRules.validate(tc, baseUrl, errors);
        return errors;
    }

    /** TC_03 from exec_fe79414458b3 — ambiguous Phone field on Facebook. */
    private static ManualTestCase facebookPhoneCase() {
        return new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Phone field
                3. Enter an incorrect password
                4. Click the Log in button
                5. Confirm a wrong-credentials or login-failed message is shown""",
                """
                1. Login page is shown
                2. Phone number is accepted
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Incorrect email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """
                5551234567
                WrongPass123

                """,
                "EXECUTE");
    }

    /** TC_02 from exec_fe79414458b3 — title says empty email but steps type email. */
    private static ManualTestCase facebookEmptyEmailCase() {
        return new ManualTestCase(
                "TC_02",
                "Invalid password login with empty email",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Email field
                3. Enter an empty value in the Password field
                4. Click the Log in button
                5. Confirm a clear validation/error state is shown""",
                """
                1. Login page is shown
                2. Email is empty
                3. Password is empty
                4. Submit is clicked
                5. Error message 'Please enter your email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """
                
                user@example.com

                """,
                "EXECUTE");
    }

    @Test
    public void rejectsStandalonePhoneFieldStep() {
        List<String> errors = check(facebookPhoneCase(), "https://www.facebook.com/");
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("email or phone")));
    }

    @Test
    public void rejectsEmptyEmailCaseWithTypedTestData() {
        List<String> errors = check(facebookEmptyEmailCase(), "https://www.facebook.com/");
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e ->
                e.toLowerCase().contains("empty") && e.toLowerCase().contains("testdata")));
    }

    @Test
    public void rejectsEmptyEmailCaseWithEnterStepNotLeaveEmpty() {
        List<String> errors = check(facebookEmptyEmailCase(), "https://www.facebook.com/");
        Assert.assertTrue(errors.stream().anyMatch(e ->
                e.toLowerCase().contains("leave") || e.toLowerCase().contains("empty email")));
    }

    @Test
    public void rejectsVagueValidationAssert() {
        List<String> errors = check(facebookEmptyEmailCase(), "https://www.facebook.com/");
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("vague")
                || e.toLowerCase().contains("validation/error")));
    }

    @Test
    public void rejectsLeaveEmptyStepWithAngleTokenTestData() {
        ManualTestCase bad = new ManualTestCase(
                "TC_02",
                "Invalid password login with empty email",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Leave the Email or phone field empty
                3. Enter in the Password field
                4. Click the Log in button
                5. Confirm the message 'Please enter your email or phone number' is visible""",
                """
                1. Login page is shown
                2. Email or phone is empty
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Please enter your email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """

<VALID_PASSWORD>
ValidPass123!

""",
                "EXECUTE");
        List<String> errors = check(bad, "https://www.facebook.com/");
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e ->
                e.toLowerCase().contains("leave-empty") || e.toLowerCase().contains("blank testdata")));
    }

    @Test
    public void rejectsVagueValidationOrErrorMessageAssert() {
        ManualTestCase bad = new ManualTestCase(
                "TC_12",
                "Form validation",
                "",
                "1. Open login\n2. Click Submit\n3. Confirm a clear validation or error message is shown",
                "Error shown",
                "", "", "", "", "AUTOMATE");
        List<String> errors = check(bad);
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("vague")
                || e.toLowerCase().contains("validation")));
    }

    @Test
    public void acceptsWellFormedFacebookEmptyEmailCase() {
        ManualTestCase good = new ManualTestCase(
                "TC_02",
                "Invalid password login with empty email",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Leave the Email or phone field empty
                3. Leave the Password field empty
                4. Click the Log in button
                5. Confirm the message 'Please enter your email or phone number' is visible""",
                """
                1. Login page is shown
                2. Email or phone is empty
                3. Password is empty
                4. Submit is clicked
                5. Error message 'Please enter your email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """



                """,
                "EXECUTE");
        Assert.assertTrue(check(good, "https://www.facebook.com/").isEmpty());
    }

    @Test
    public void acceptsWellFormedFacebookPhoneLoginCase() {
        ManualTestCase good = new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Email or phone field
                3. Enter in the Password field
                4. Click the Log in button
                5. Confirm the message 'Incorrect email or phone number' is visible""",
                """
                1. Login page is shown
                2. Phone number is accepted
                3. Password is accepted
                4. Submit is clicked
                5. Error message 'Incorrect email or phone number' is shown""",
                "P1",
                "negative",
                "",
                """
                5551234567
                WrongPass123

                """,
                "EXECUTE");
        Assert.assertTrue(check(good, "https://www.facebook.com/").isEmpty());
    }

    @Test
    public void rejectsVagueVerifyErrorAppearsWithoutQuote() {
        ManualTestCase bad = new ManualTestCase(
                "TC_13",
                "Form validation",
                "",
                "1. Open login\n2. Click Submit\n3. Verify an error appears",
                "Error shown",
                "", "", "", "", "AUTOMATE");
        List<String> errors = check(bad);
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("vague")));
    }

    @Test
    public void acceptsAssertWithQuotedExactMessage() {
        ManualTestCase good = new ManualTestCase(
                "TC_14",
                "Form validation",
                "",
                "1. Open login\n2. Click Submit\n3. Confirm the message 'Please enter your email' is visible",
                "Error shown",
                "", "", "", "", "AUTOMATE");
        Assert.assertTrue(check(good).isEmpty(), check(good).toString());
    }

    @Test
    public void rejectsDoNotFillWithNonBlankTestData() {
        ManualTestCase bad = new ManualTestCase(
                "TC_15",
                "Empty email",
                "",
                "1. Open login\n2. Do not fill the email field\n3. Click Submit",
                "Error",
                "", "", "", "\nshould-not\n", "EXECUTE");
        List<String> errors = check(bad);
        Assert.assertTrue(errors.stream().anyMatch(e ->
                e.toLowerCase().contains("leave-empty") || e.toLowerCase().contains("blank testdata")),
                errors.toString());
    }

    @Test
    public void allowsStandalonePhoneFieldWhenSeparateFieldImplied() {
        ManualTestCase separatePhone = new ManualTestCase(
                "TC_10",
                "Login with phone OTP",
                "",
                "1. Open login\n2. Enter in the Phone field\n3. Click Send code",
                "1. Page shown\n2. Phone number accepted\n3. Code sent",
                "", "", "", "5551234567\n", "AUTOMATE");
        Assert.assertTrue(check(separatePhone).isEmpty());
    }

    @Test
    public void rejectsStandaloneEmailWhenExpectedMentionsEmailOrPhone() {
        ManualTestCase bad = new ManualTestCase(
                "TC_11",
                "Invalid login",
                "",
                "1. Open login\n2. Enter in the Email field\n3. Enter in the Password field",
                "Error 'Incorrect email or phone number' is shown",
                "", "", "", "user@test.com\npass", "AUTOMATE");
        List<String> errors = check(bad);
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("email or phone")));
    }
}
