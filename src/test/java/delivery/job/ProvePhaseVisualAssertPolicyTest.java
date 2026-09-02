package delivery.job;

import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.vision.VisionAssertionResult;
import delivery.vision.VisionAssertionStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ProvePhaseVisualAssertPolicyTest {

    @Test
    public void uncertainDoesNotDemote() {
        Assert.assertFalse(ProvePhase.shouldDemoteForVisual(
                VisionAssertionResult.uncertain("echo"),
                "Facebook registration form is visible",
                "https://web.example.com/reg/"));
    }

    @Test
    public void placeholderHonestyUncertainDemotes() {
        Assert.assertTrue(ProvePhase.shouldDemoteForVisual(
                VisionAssertionResult.uncertain("visual PASS used prompt placeholder text"),
                "Checkout complete page is visible with Thank you for your order!",
                "https://www.saucedemo.com/checkout-complete.html"),
                "SauceDemo-style placeholder PASS must not leave a silent green TC");
    }

    @Test
    public void lowConfidenceFailDoesNotDemote() {
        Assert.assertFalse(ProvePhase.shouldDemoteForVisual(
                VisionAssertionResult.of(VisionAssertionStatus.FAIL, 0.0, "no", "empty"),
                "Facebook registration form is visible",
                "https://web.example.com/reg/"));
    }

    @Test
    public void registrationClaimOnLoginUrlDemotes() {
        Assert.assertTrue(ProvePhase.shouldDemoteForVisual(
                VisionAssertionResult.of(VisionAssertionStatus.FAIL, 0.9, "login page", "url"),
                "The registration form is still shown",
                "https://web.example.com/login/"));
    }

    @Test
    public void confidentFailOnTargetPageDemotes() {
        Assert.assertTrue(ProvePhase.shouldDemoteForVisual(
                VisionAssertionResult.of(VisionAssertionStatus.FAIL, 0.85, "heading missing", "pixels"),
                "Welcome heading is visible",
                "https://example.com/home"));
    }

    @Test
    public void passNeverDemotes() {
        Assert.assertFalse(ProvePhase.shouldDemoteForVisual(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.9, "form", "fields"),
                "registration form is visible",
                "https://web.example.com/reg/"));
    }

    @Test
    public void refusesSubmitClickOnRegisterPathHref() {
        ProvenStep selfNav = new ProvenStep(
                "TC1", "Page", "elementAction", "click",
                "css", "a[href='https://web.example.com/reg/']", "", "", "", true,
                "intent:CLICK");
        Assert.assertTrue(ProvePhase.refusesSubmitNavigation(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click the Submit button"),
                List.of(selfNav)));
    }
}
