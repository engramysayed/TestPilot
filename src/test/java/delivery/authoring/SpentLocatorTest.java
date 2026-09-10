package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * A later field intent must not land on a control this TC already filled. The verb Select and
 * leftover pronouns (your) are not a field name — they match every "Select day" combobox.
 */
public class SpentLocatorTest {

    private static final String FORM = """
            <html><body><form>
              <div role="combobox" aria-label="Select day"></div>
              <div role="combobox" aria-label="Select month"></div>
              <div role="combobox" aria-label="Select year"></div>
              <div role="combobox" aria-label="Select your gender"></div>
            </form></body></html>
            """;

    private static ProvenStep select(String locator) {
        return new ProvenStep("TC1", "Page", "elementAction", "select",
                "css", locator, "15", "", "", true, "intent:TYPE_FIELD");
    }

    @Test
    public void genderIntentDoesNotShareATokenWithTheDayCombobox() {
        DomCandidate day = new DomCandidate(
                "d1", "css", "div[aria-label='Select day']", "combobox", "Select day");

        Assert.assertFalse(StepIntentBinder.candidateSharesFieldToken(
                "Select Female from the Select your gender dropdown", day),
                "the verb Select and the pronoun your must not satisfy a gender intent");
    }

    @Test
    public void bindSkipsAlreadyFilledComboboxes() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(FORM);
        List<ProvenStep> spent = List.of(
                select("div[aria-label='Select day']"),
                select("div[aria-label='Select month']"),
                select("div[aria-label='Select year']"));

        ManualTestCase tc = new ManualTestCase(
                "TC1", "Register", "",
                "1. Select Female from the Select your gender dropdown",
                "Gender shows Female", "P1", "");

        StepIntentBinder.BindResult result = StepIntentBinder.bind(
                tc, StepIntentBinder.withoutSpentControls(candidates, spent));

        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertTrue(result.steps().get(0).locatorValue().toLowerCase().contains("gender"),
                "expected the gender control, got " + result.steps().get(0).locatorValue());
    }

    @Test
    public void clickSkipsTheInputThisTcAlreadyTyped() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "basic_otp", "input", "OTP"),
                new DomCandidate("c2", "css", "button[data-axis-test-id='verify_Otp_Button']",
                        "button", "Verify OTP"));
        List<ProvenStep> spent = List.of(new ProvenStep(
                "TC1", "Page", "elementAction", "type",
                "id", "basic_otp", "245345", "", "", true, "intent:TYPE_FIELD"));
        List<DomCandidate> live = StepIntentBinder.withoutSpentControls(candidates, spent);
        Assert.assertTrue(live.stream().noneMatch(c -> "basic_otp".equals(c.value())),
                "typed OTP field must be spent for a later click");

        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click the Verify OTP button"),
                "TC1", live, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertFalse(result.steps().get(0).locatorValue().contains("basic_otp"));
    }

    @Test
    public void typingOtpViaPreferredHookSpendsIdTwin() {
        // AxisPay: first type uses data-axis-test-id; id=basic_otp must not be typed again.
        String html = """
                <form>
                  <input id="basic_otp" data-axis-test-id="otp-input"/>
                  <button data-axis-test-id="verify_Otp_Button">Verify OTP</button>
                </form>
                """;
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "[data-axis-test-id='otp-input']", "input", "OTP"),
                new DomCandidate("c2", "id", "basic_otp", "input", "OTP"),
                new DomCandidate("c3", "css", "button[data-axis-test-id='verify_Otp_Button']",
                        "button", "Verify OTP"));
        List<ProvenStep> spent = List.of(new ProvenStep(
                "TC1", "Page", "elementAction", "type",
                "css", "[data-axis-test-id='otp-input']", "245345", "", "", true, "intent:TYPE_FIELD"));
        List<DomCandidate> live = StepIntentBinder.withoutSpentControls(candidates, spent, html);
        Assert.assertTrue(live.stream().noneMatch(c -> "basic_otp".equals(c.value())),
                "OTP id twin must be spent after preferred-hook type: " + live);
        Assert.assertTrue(live.stream().noneMatch(c ->
                        c.value() != null && c.value().contains("otp-input")),
                "preferred OTP locator must stay spent");
    }

    @Test
    public void typingViaPreferredHookSpendsIdTwinForAnyField() {
        String html = """
                <form>
                  <input id="firstName" data-axis-test-id="first-name"/>
                  <input id="lastName" data-axis-test-id="last-name"/>
                </form>
                """;
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "[data-axis-test-id='first-name']", "input", "First name"),
                new DomCandidate("c2", "id", "firstName", "input", "First name"),
                new DomCandidate("c3", "id", "lastName", "input", "Last name"));
        List<ProvenStep> spent = List.of(new ProvenStep(
                "TC1", "Page", "elementAction", "type",
                "css", "[data-axis-test-id='first-name']", "Ada", "", "", true, "intent:TYPE_FIELD"));
        List<DomCandidate> live = StepIntentBinder.withoutSpentControls(candidates, spent, html);
        Assert.assertTrue(live.stream().noneMatch(c -> "firstName".equals(c.value())),
                "id twin of preferred-hook field must be spent: " + live);
        Assert.assertTrue(live.stream().anyMatch(c -> "lastName".equals(c.value())),
                "unrelated field must remain bindable");
    }

    @Test
    public void assertCanStillTargetAControlThisTcAlreadyFilled() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(FORM);
        List<ProvenStep> spent = List.of(select("div[aria-label='Select day']"));

        ManualTestCase tc = new ManualTestCase(
                "TC1", "Register", "",
                "1. Confirm the Day dropdown is visible",
                "Day is shown", "P1", "");

        StepIntentBinder.BindResult result = StepIntentBinder.bind(tc, candidates);
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertTrue(result.steps().get(0).locatorValue().toLowerCase().contains("day"),
                "an assert must still see the control that was just filled, got "
                        + result.steps().get(0).locatorValue());
        Assert.assertEquals(
                StepIntentBinder.withoutSpentControls(candidates, spent).size()
                        < candidates.size(),
                true,
                "spent filter is only for later fills — asserts keep the original table");
    }
}
