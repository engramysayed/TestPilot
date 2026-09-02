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
