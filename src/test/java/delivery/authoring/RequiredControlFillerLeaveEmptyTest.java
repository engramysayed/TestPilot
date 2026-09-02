package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RequiredControlFillerLeaveEmptyTest {

    private static final String LOGIN_HTML = """
            <body>
            <form>
              <input id="email" name="email" data-test="email" placeholder="Email or phone" value=""/>
              <input id="pass" name="pass" type="password" data-test="pass" placeholder="Password" value=""/>
              <button type="submit" name="login">Log in</button>
            </form>
            </body>
            """;

    @Test
    public void submitClick_doesNotAutofillLeaveEmptyEmail() {
        List<StepIntentBinder.IntentLine> intents = List.of(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD,
                        "Leave the Email or phone field empty",
                        ""),
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD,
                        "Enter in the Password field",
                        "Secret1!")
        );
        // Use Submit-like wording so auto-fill engages (same path as Continue/Submit forms).
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                LOGIN_HTML, "TC_02", "Click Log in", intents);
        Assert.assertTrue(
                fills.stream().noneMatch(s ->
                        "type".equalsIgnoreCase(s.action())
                                && s.locatorValue() != null
                                && s.locatorValue().toLowerCase().contains("email")),
                "leave-empty email must not be auto-filled: " + fills);
    }

    @Test
    public void logInClick_withoutLeaveEmpty_fillsEmail() {
        List<StepIntentBinder.IntentLine> intents = List.of(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD,
                        "Enter in the Email or phone field",
                        "user@example.com"),
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD,
                        "Enter in the Password field",
                        "Secret1!")
        );
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                LOGIN_HTML, "TC_01", "Click Log in", intents);
        Assert.assertTrue(
                fills.stream().anyMatch(s ->
                        "type".equalsIgnoreCase(s.action())
                                && s.locatorValue() != null
                                && s.locatorValue().toLowerCase().contains("email")),
                "Log in should trigger autofill for email: " + fills);
    }

    @Test
    public void submitClick_withoutLeaveEmpty_stillFillsEmail() {
        List<StepIntentBinder.IntentLine> intents = List.of(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD,
                        "Enter in the Email or phone field",
                        "user@example.com"),
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD,
                        "Enter in the Password field",
                        "Secret1!")
        );
        List<ProvenStep> fills = RequiredControlFiller.planFillsBeforeClick(
                LOGIN_HTML, "TC_01", "Click the Submit button", intents);
        Assert.assertTrue(
                fills.stream().anyMatch(s ->
                        "type".equalsIgnoreCase(s.action())
                                && s.locatorValue() != null
                                && s.locatorValue().toLowerCase().contains("email")
                                && "user@example.com".equals(s.value())),
                "non-empty email intent should still auto-fill: " + fills);
    }
}
