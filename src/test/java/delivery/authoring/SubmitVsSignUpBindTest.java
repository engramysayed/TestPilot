package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Excel "Click the Submit button" must prefer a submit/sign-up <strong>button</strong>
 * over a bare Sign up anchor when both score as form-submit CTAs.
 * <p>
 * Re-emitting Facebook IR alone cannot fix Submit — the proven locator stays the Sign-up
 * link until a live re-prove (plan Task 2B) refreshes IR after this bind preference lands.
 */
public class SubmitVsSignUpBindTest {

    @Test
    public void facebookShapedSubmitPrefersButtonNotSignUpAnchor() {
        // Same Sign-up label; locator must NOT contain the word "submit" or token
        // overlap alone would hide the historic anchor +1 preference bug.
        List<DomCandidate> candidates = List.of(
                new DomCandidate("btn", "css", "button[name='signup']", "button", "Sign up"),
                new DomCandidate("lnk", "xpath",
                        "//a[contains(normalize-space(.),'Sign up')]"
                                + "[not(.//*[contains(normalize-space(.),'Sign up')])]",
                        "a", "Sign up"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Submit button");
        StepIntentBinder.BindResult r =
                StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorValue(), "button[name='signup']",
                "must prefer Sign up button over bare Sign up link, got: "
                        + r.steps().get(0).locatorValue());
    }

    @Test
    public void submitIntentPrefersSignUpButtonOverBareAnchor() {
        facebookShapedSubmitPrefersButtonNotSignUpAnchor();
    }

    @Test
    public void submitIntentPrefersLiteralSubmitOverSignUpSynonym() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("sub", "css", "button[name='websubmit']", "button", "Submit"),
                new DomCandidate("lnk", "xpath",
                        "//a[contains(normalize-space(.),'Sign up')]"
                                + "[not(.//*[contains(normalize-space(.),'Sign up')])]",
                        "a", "Sign up"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Submit button");
        StepIntentBinder.BindResult r =
                StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().toLowerCase().contains("websubmit")
                        || r.steps().get(0).locatorValue().toLowerCase().contains("submit"),
                "expected literal Submit control, got: " + r.steps().get(0).locatorValue());
        Assert.assertFalse(r.steps().get(0).locatorValue().toLowerCase().contains("sign up"));
    }
}
