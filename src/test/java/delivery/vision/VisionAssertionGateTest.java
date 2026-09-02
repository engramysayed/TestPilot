package delivery.vision;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VisionAssertionGateTest {
    @Test
    public void rejectsPromptSchemaPlaceholders() {
        VisionAssertionResult raw = VisionAssertionResult.of(
                VisionAssertionStatus.PASS, 0.9, "what is visible", "why");
        VisionAssertionResult out = VisionAssertionGate.honestyCheck(
                raw, "Checkout complete page is visible with Thank you for your order!", null);
        Assert.assertNotEquals(out.status(), VisionAssertionStatus.PASS);
        Assert.assertEquals(out.status(), VisionAssertionStatus.UNCERTAIN);
        Assert.assertTrue(out.error().toLowerCase().contains("placeholder"), out.error());
    }

    @Test
    public void sauceDemoPlaceholderPassBecomesUncertain() {
        VisionAssertionResult raw = VisionAssertionResult.of(
                VisionAssertionStatus.PASS, 0.9,
                "what is visible", "why");
        VisionAssertionResult checked = VisionAssertionGate.honestyCheck(
                raw,
                "Checkout complete page is visible with Thank you for your order!",
                "https://www.saucedemo.com/checkout-complete.html");
        Assert.assertEquals(checked.status(), VisionAssertionStatus.UNCERTAIN);
        Assert.assertTrue(checked.error().toLowerCase().contains("placeholder"), checked.error());
    }

    @Test
    public void evaluateAppliesHonestyGateViaProvider() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        try {
            ManualTestCase tc = new ManualTestCase(
                    "TC_SD_E2E_01", "Checkout", "",
                    "1. Finish checkout",
                    "Thank you", "P1", "",
                    "Checkout complete page is visible with Thank you for your order!");
            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    VisionAssertionResult.of(
                            VisionAssertionStatus.PASS, 0.9, "what is visible", "why"));
            VisionAssertionResult out = VisionAssertionGate.evaluate(
                    tc, new byte[]{1, 2, 3}, fake, "https://www.saucedemo.com/checkout-complete.html");
            Assert.assertNotNull(out);
            Assert.assertEquals(out.status(), VisionAssertionStatus.UNCERTAIN);
            Assert.assertTrue(out.error().toLowerCase().contains("placeholder"), out.error());
        } finally {
            System.clearProperty("delivery.vision.assertions.enabled");
        }
    }

    @Test
    public void rejectsVeryShortEvidenceOnPass() {
        VisionAssertionResult raw = VisionAssertionResult.of(
                VisionAssertionStatus.PASS, 0.9, "ok", "ok");
        VisionAssertionResult out = VisionAssertionGate.honestyCheck(
                raw, "Thank you for your order!", null);
        Assert.assertNotEquals(out.status(), VisionAssertionStatus.PASS);
    }

    @Test
    public void acceptsConcreteObservation() {
        VisionAssertionResult raw = VisionAssertionResult.of(
                VisionAssertionStatus.PASS, 0.92,
                "Green checkmark and heading Thank you for your order! on checkout complete",
                "Visible success banner matches assertion");
        VisionAssertionResult out = VisionAssertionGate.honestyCheck(
                raw, "Checkout complete page is visible with Thank you for your order!", null);
        Assert.assertEquals(out.status(), VisionAssertionStatus.PASS);
    }
}
