package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GenerateAuthoringRepairTest {

    private static final String BASE = "https://www.facebook.com/";

    static ManualTestCase emptyEmailTypedData() {
        return new ManualTestCase(
                "TC_02",
                "Login with empty email and valid password",
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
                "negative,missing-credentials",
                "",
                """

                user@example.com
                ValidPass123!

                """,
                "EXECUTE");
    }

    @Test
    public void repair_blanksEmptyEmailTestData_keepsPasswordLine() {
        ManualTestCase raw = emptyEmailTypedData();
        Assert.assertFalse(GenerateQualityGate.validate(List.of(raw), BASE).isEmpty());

        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        Assert.assertEquals(out.size(), 1);
        List<String> errors = GenerateQualityGate.validate(out, BASE);
        Assert.assertTrue(errors.isEmpty(), errors.toString());

        List<String> data = GenerateAuthoringRules.splitTestDataLines(out.get(0).testData(), 5);
        Assert.assertEquals(data.get(1).trim(), "", "empty-email step TestData must be blank");
        Assert.assertEquals(data.get(2).trim(), "ValidPass123!");
    }

    @Test
    public void repair_idempotentOnAlreadyBlankEmptyEmail() {
        ManualTestCase good = emptyEmailTypedData();
        List<String> lines = GenerateAuthoringRules.splitTestDataLines(good.testData(), 5);
        lines.set(1, "");
        ManualTestCase already = new ManualTestCase(
                good.tcId(), good.title(), good.preconditions(), good.steps(),
                good.expectedResult(), good.priority(), good.tags(), good.visualAssertion(),
                String.join("\n", lines), good.keelPath());
        Assert.assertTrue(GenerateQualityGate.validate(List.of(already), BASE).isEmpty());

        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(already));
        Assert.assertEquals(out.get(0).testData(), already.testData());
        Assert.assertEquals(out.get(0).steps(), already.steps());
    }

    @Test
    public void repair_nullOrEmpty_doesNotThrow() {
        Assert.assertTrue(GenerateAuthoringRepair.repair(null).isEmpty());
        Assert.assertTrue(GenerateAuthoringRepair.repair(List.of()).isEmpty());
    }

    @Test
    public void repair_rewritesStandalonePhoneFieldOnCombinedLogin() {
        ManualTestCase raw = new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Phone field
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
                WrongPass123!

                """,
                "EXECUTE");
        Assert.assertFalse(GenerateQualityGate.validate(List.of(raw), BASE).isEmpty());
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        List<String> errors = GenerateQualityGate.validate(out, BASE);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertTrue(out.get(0).steps().toLowerCase().contains("email or phone field"));
        Assert.assertFalse(out.get(0).steps().matches("(?is).*enter in the phone field.*"));
    }

    @Test
    public void repair_rewritesEnterToLeaveEmptyOnEmptyEmailCase() {
        ManualTestCase raw = new ManualTestCase(
                "TC_02",
                "Login with empty email and valid password",
                "No login required.",
                """
                1. Open the Login Page at https://www.facebook.com/
                2. Enter in the Email or phone field
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

                ValidPass123!

                """,
                "EXECUTE");
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        Assert.assertTrue(GenerateQualityGate.validate(out, BASE).isEmpty(),
                GenerateQualityGate.validate(out, BASE).toString());
        String step2 = GenerateAuthoringRules.splitNumberedSteps(out.get(0).steps()).get(1);
        Assert.assertTrue(step2.toLowerCase().contains("leave the"));
        Assert.assertTrue(step2.toLowerCase().contains("email or phone"));
    }

    @Test
    public void repair_turnsLiteralBackslashNIntoNewlines() {
        String jammed = "1. Open login\\n2. Leave the Email or phone field empty\\n"
                + "3. Leave the Password field empty\\n4. Click Log in\\n"
                + "5. Confirm the message 'Please enter your email or phone number' is visible";
        ManualTestCase raw = new ManualTestCase(
                "TC_01",
                "Login with empty email and empty password",
                "",
                jammed,
                "1. Login page is shown\\n2. Email or phone is empty\\n3. Password is empty\\n"
                        + "4. Submit is clicked\\n5. Error message 'Please enter your email or phone number' is shown",
                "P1",
                "negative",
                "",
                "\n\n\n",
                "EXECUTE");
        Assert.assertTrue(GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(raw.steps()));
        List<ManualTestCase> out = GenerateAuthoringRepair.repair(List.of(raw));
        Assert.assertFalse(GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(out.get(0).steps()));
        Assert.assertTrue(out.get(0).steps().contains("\n"));
        Assert.assertTrue(GenerateQualityGate.validate(out, BASE).isEmpty(),
                GenerateQualityGate.validate(out, BASE).toString());
    }
}
