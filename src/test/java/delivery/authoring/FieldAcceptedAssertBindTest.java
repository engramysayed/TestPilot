package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Excel expected "Username is accepted" after a fill must bind the field, not AMBIGUOUS
 * a label against the input. Do not rewrite the workbook.
 */
public class FieldAcceptedAssertBindTest {

    /** Same stability band (both carry data-axis-test-id) so decide() is TIE — live Axis c6/c7. */
    private static final List<DomCandidate> USERNAME_INPUT_AND_HINT = List.of(
            new DomCandidate("c6", "css", "input[data-axis-test-id='username_Input']", "input", "Username"),
            new DomCandidate("c7", "css", "span[data-axis-test-id='username_Hint']", "span", "Username")
    );

    private static final List<DomCandidate> TWO_USERNAME_INPUTS = List.of(
            new DomCandidate("c1", "css", "input[data-axis-test-id='username_Input']", "input", "Username"),
            new DomCandidate("c2", "css", "input[data-axis-test-id='username_Confirm']", "input", "Username")
    );

    @Test
    public void usernameAccepted_picksInputOverLabel() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Username is accepted.");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC_01", USERNAME_INPUT_AND_HINT, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("username_Input"),
                r.steps().get(0).locatorValue());
        Assert.assertEquals(r.steps().get(0).assertionType(), "visible");
    }

    @Test
    public void twoUsernameInputs_stayAmbiguousWithoutSpent() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Username is accepted.");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC_01", TWO_USERNAME_INPUTS, List.of());
        Assert.assertFalse(r.ok(), r.rejectReason());
        Assert.assertTrue(r.rejectReason().startsWith("AMBIGUOUS:"), r.rejectReason());
    }

    @Test
    public void spentFill_resolvesAcceptedAssertToThatField() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Username is accepted.");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC_01", TWO_USERNAME_INPUTS, List.of("c1"));
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("username_Input"),
                r.steps().get(0).locatorValue());
    }

    @Test
    public void authorIntent_usesSpentUsernameFill_forAcceptedAssert() throws Exception {
        String html = """
                <html><body>
                  <input data-axis-test-id="username_Input" aria-label="Username">
                  <input data-axis-test-id="username_Confirm" aria-label="Username">
                  <input data-axis-test-id="password_Input" type="password" aria-label="Password">
                </body></html>
                """;
        ProvenStep typed = new ProvenStep(
                "TC_01", "LoginPage", "elementAction", "type",
                "css", "input[data-axis-test-id='username_Input']", "${TARGET_USERNAME}",
                "", "", true, "memory:TYPE_USER");
        AuthoringService authoring = new AuthoringService(
                new LocalLlmClient("http://127.0.0.1:9", "dummy"),
                new LocatorValidator());
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Username is accepted.");
        List<ProvenStep> steps = authoring.authorIntent(
                "TC_01", intent, html, null, false, List.of(typed), List.of());
        Assert.assertFalse(steps.isEmpty(), "accepted assert must bind");
        Assert.assertTrue(steps.get(0).validated(), steps.get(0).rationale());
        Assert.assertTrue(steps.get(0).locatorValue().contains("username_Input"),
                steps.get(0).locatorValue());
        Assert.assertEquals(steps.get(0).action(), "assert");
    }
}
