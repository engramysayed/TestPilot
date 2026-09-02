package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class VisualAssertionGateTest {

    @AfterMethod
    public void clearFlags() {
        System.clearProperty("delivery.vision.assertions.enabled");
    }

    @Test
    public void shouldRun_falseWhenFlagOff() {
        System.setProperty("delivery.vision.assertions.enabled", "false");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "Welcome visible");
        Assert.assertFalse(VisionAssertionGate.shouldRun(tc));
    }

    @Test
    public void shouldRun_falseWhenCellBlank() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "  ");
        Assert.assertFalse(VisionAssertionGate.shouldRun(tc));
    }

    @Test
    public void shouldRun_trueWhenFlagOnAndTextPresent() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "Welcome visible");
        Assert.assertTrue(VisionAssertionGate.shouldRun(tc));
    }

    @Test
    public void evaluate_skipReturnsNull() {
        System.setProperty("delivery.vision.assertions.enabled", "false");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "Welcome visible");
        Assert.assertNull(VisionAssertionGate.evaluate(
                tc, new byte[] {1}, new FakeVisionGroundingProvider()));
    }

    @Test
    public void evaluate_failStatus() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "Welcome visible");
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                VisionAssertionResult.of(VisionAssertionStatus.FAIL, 0.8, "no", "blank"));
        VisionAssertionResult r = VisionAssertionGate.evaluate(tc, new byte[] {1, 2}, fake);
        Assert.assertEquals(r.status(), VisionAssertionStatus.FAIL);
    }

    @Test
    public void evaluate_lowConfidencePassIsUncertain() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "Welcome visible");
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.4, "welcome", "heading"));
        VisionAssertionResult r = VisionAssertionGate.evaluate(tc, new byte[] {1, 2}, fake);
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
    }

    @Test
    public void evaluate_echoedAssertionTextIsUncertain() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        String claim = "The registration form is still shown and the Password field is visible";
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", claim);
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.9, claim, claim));
        VisionAssertionResult r = VisionAssertionGate.evaluate(tc, new byte[] {1, 2}, fake);
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
    }

    @Test
    public void evaluate_paraphrasedAssertionTextIsUncertain() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        String claim = "The registration form is still shown and the Password field is visible";
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", claim);
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                VisionAssertionResult.of(
                        VisionAssertionStatus.PASS, 0.95,
                        "registration form still shown password field visible",
                        "same words"));
        VisionAssertionResult r = VisionAssertionGate.evaluate(tc, new byte[] {1, 2}, fake);
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
    }

    @Test
    public void evaluate_registrationClaimOnLoginUrlFails() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "",
                "The registration form is still shown");
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.9,
                        "form visible with several fields", "input fields are shown on screen"));
        VisionAssertionResult r = VisionAssertionGate.evaluate(
                tc, new byte[] {1, 2}, fake, "https://web.example.com/login/");
        Assert.assertEquals(r.status(), VisionAssertionStatus.FAIL);
    }

    @Test
    public void evaluate_emptyPngIsUncertain() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC1", "t", "", "click", "ok", "", "", "Welcome visible");
        VisionAssertionResult r = VisionAssertionGate.evaluate(
                tc, new byte[0], new FakeVisionGroundingProvider(
                        VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.9, "ok", "ui")));
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
    }
}
