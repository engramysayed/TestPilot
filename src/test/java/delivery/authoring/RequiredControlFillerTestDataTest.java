package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RequiredControlFillerTestDataTest {

    private static final String HTML = """
            <body>
            <form>
              <input id="first-name" name="firstName" data-test="firstName" placeholder="First Name" required/>
              <input id="last-name" name="lastName" data-test="lastName" placeholder="Last Name" required/>
              <input id="postal-code" name="postalCode" data-test="postalCode" placeholder="Zip/Postal Code" required/>
              <button type="submit">Continue</button>
            </form>
            </body>
            """;

    @Test
    public void autoFillBeforeContinueUsesExcelTestData() {
        List<StepIntentBinder.IntentLine> intents = List.of(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter in the First Name field", "John"),
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter in the Last Name field", "Doe"),
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter in the Zip/Postal Code field", "12345")
        );
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                HTML, "TC_1", "Click the Continue button", intents);
        Assert.assertFalse(fills.isEmpty(), "expected auto-fill steps");
        Assert.assertEquals(valueFor(fills, "first-name", "firstName"), "John");
        Assert.assertEquals(valueFor(fills, "last-name", "lastName"), "Doe");
        Assert.assertEquals(valueFor(fills, "postal-code", "postalCode"), "12345");
    }

    @Test
    public void verifyOtpClickDoesNotRetypeOtp() {
        String html = """
                <form>
                  <input id="basic_otp" name="otp" required value=""/>
                  <button type="button">Verify OTP</button>
                </form>
                """;
        List<StepIntentBinder.IntentLine> intents = List.of(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter the static OTP 245345", "245345")
        );
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                html, "TC_1", "Click the Verify OTP button", intents);
        Assert.assertTrue(fills.isEmpty(), "Verify OTP must not re-type OTP: " + fills);
    }

    @Test
    public void submitOtpWordingDoesNotTriggerAutofill() {
        // Excel often says "submit OTP" — must not match generic submit autofill.
        String html = """
                <form>
                  <input id="basic_otp" name="otp" type="password" required value=""/>
                  <button type="button">Submit OTP</button>
                </form>
                """;
        Assert.assertFalse(RequiredControlFiller.looksLikeSubmit("Click the submit OTP button"));
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                html, "TC_1", "Click the submit OTP button", List.of());
        Assert.assertTrue(fills.isEmpty(), "submit OTP must not autofill: " + fills);
    }

    @Test
    public void autofillSkipsControlAlreadyTypedViaPreferredHook() {
        String html = """
                <form>
                  <input id="firstName" name="firstName" data-axis-test-id="first-name" required value=""/>
                  <button type="submit">Continue</button>
                </form>
                """;
        List<ProvenStep> spent = List.of(new ProvenStep(
                "TC_1", "Page", "elementAction", "type",
                "css", "[data-axis-test-id='first-name']", "Ada", "", "", true, "intent:TYPE_FIELD"));
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                html, "TC_1", "Click the Continue button", List.of(), spent);
        Assert.assertTrue(fills.isEmpty(),
                "must not re-type same field via id twin after preferred-hook type: " + fills);
    }

    private static String valueFor(List<ProvenStep> fills, String... locatorBits) {
        for (ProvenStep step : fills) {
            if (!"type".equalsIgnoreCase(step.action())) {
                continue;
            }
            String loc = step.locatorValue() == null ? "" : step.locatorValue();
            for (String bit : locatorBits) {
                if (loc.equals(bit) || loc.contains(bit)) {
                    return step.value();
                }
            }
        }
        Assert.fail("no type step for " + String.join(",", locatorBits));
        return "";
    }
}
