package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GenerateQualityGateTest {

    private static ManualTestCase tc(String tcId, String steps, String keelPath) {
        return new ManualTestCase(
                tcId, "Title", "", steps, "Expected", "", "", "", "", keelPath);
    }

    @Test
    public void validate_acceptsWellFormedCase() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("TC_01", "1. Open login", "AUTOMATE"),
                tc("TC_DQ_01", "1. Do thing", "")));
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_acceptsKnownKeelPathAliases() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("TC_02", "1. Step", "AUTO"),
                tc("TC_03", "1. Step", "RUN")));
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_rejectsInvalidTcId() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("LOGIN_01", "1. Open", "AUTOMATE")));
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.contains("TC_01") || e.contains("tcId")));
    }

    @Test
    public void validate_rejectsStepLikeProseInTcId() {
        String longId = "TC_" + "A".repeat(33);
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc(longId, "1. Open", "AUTOMATE")));
        Assert.assertFalse(errors.isEmpty());
        Assert.assertEquals(errors.size(), 1);
        Assert.assertTrue(errors.get(0).contains("looks like step prose, not an identifier"));
        Assert.assertTrue(errors.get(0).contains(longId));
    }

    @Test
    public void validate_rejectsBlankStepsAfterNormalize() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("TC_01", "   ", "AUTOMATE")));
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("step")));
    }

    @Test
    public void validate_rejectsLiteralBackslashNWithoutRealNewlines() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("TC_01", "1. Open login\\n2. Submit form", "AUTOMATE")));
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.contains("\\n") || e.toLowerCase().contains("newline")));
    }

    @Test
    public void validate_acceptsRealNewlinesInSteps() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("TC_01", "1. Open login\n2. Submit form", "AUTOMATE")));
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_rejectsCombinedFormBadPhoneStep() {
        ManualTestCase badPhone = new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "",
                "1. Open login\n2. Enter in the Phone field\n3. Enter in the Password field",
                "Error message 'Incorrect email or phone number' is shown",
                "", "", "", "", "EXECUTE");
        List<String> errors = GenerateQualityGate.validate(List.of(badPhone));
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("email or phone")));
    }

    @Test
    public void validate_rejectsUnknownKeelPath() {
        List<String> errors = GenerateQualityGate.validate(List.of(
                tc("TC_01", "1. Open", "NOT_A_PATH")));
        Assert.assertFalse(errors.isEmpty());
        Assert.assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("keelpath")));
    }
}
