package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.LocatorValidator;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ollama picks a row; Cursor is allowed to say no row fits and write the locator itself. Both
 * answers go through the same validation, so a smarter model cannot smuggle in a wrong control.
 */
public class CursorSolveTest {

    private static final String FORM = """
            <html><body><form>
              <label><input type="text" id="_r_3_"><span>First name</span></label>
              <div role="button" aria-label="Back">Back</div>
            </form></body></html>
            """;

    private static LocalLlmClient failingOllama(AtomicInteger calls) {
        return new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                calls.incrementAndGet();
                throw new RuntimeException("ollama down");
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
    }

    private static CursorHealClient solvingWith(String json) {
        return new CursorHealClient(false, "unused", 5) {
            @Override
            public String solve(String intentText, String failureReason, String shortlistTable,
                                String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                List<String> priorSteps) {
                return json;
            }
        };
    }

    private static HealCascade cascadeWith(LocalLlmClient llm, CursorHealClient cursor) {
        return new HealCascade(
                new AuthoringService(llm, new LocatorValidator()),
                cursor,
                new FreeInventHealer(cursor, null, new LocatorValidator(), "cursor", true),
                true);
    }

    @Test
    public void acceptsALocatorCursorWroteInsteadOfARow() {
        CursorHealClient cursor = solvingWith("""
                {"thought":"the input carries no name, anchor on its label","steps":[
                  {"action":"type","locatorStrategy":"xpath",
                   "locatorValue":"//label[contains(normalize-space(.),'First name')]//input",
                   "value":"Ahmed","assertionType":"","assertionExpected":""}
                ]}
                """);
        HealCascade cascade = cascadeWith(failingOllama(new AtomicInteger()), cursor);

        HealResult result = cascade.heal("TC_SOLVE",
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter Ahmed in the First name field"),
                FORM, new byte[]{1}, "bind failed", null, true);

        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "invent");
        Assert.assertEquals(result.steps().get(0).locatorValue(),
                "//label[contains(normalize-space(.),'First name')]//input");
    }

    @Test
    public void refusesAWrittenLocatorThatMissesTheFieldName() {
        CursorHealClient cursor = solvingWith("""
                {"thought":"guessing","steps":[
                  {"action":"type","locatorStrategy":"css","locatorValue":"div[aria-label='Back']",
                   "value":"Ahmed","assertionType":"","assertionExpected":""}
                ]}
                """);
        HealCascade cascade = cascadeWith(failingOllama(new AtomicInteger()), cursor);

        HealResult result = cascade.heal("TC_WRONG",
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter Ahmed in the First name field"),
                FORM, new byte[]{1}, "bind failed", null, true);

        Assert.assertFalse(result.ok(),
                "typing the first name into the Back button must never be accepted");
    }

    @Test
    public void skipsTheRowPickerWhenNoRowCarriesTheName() {
        AtomicInteger ollamaCalls = new AtomicInteger();
        CursorHealClient cursor = solvingWith("""
                {"thought":"no such field on this page","steps":[]}
                """);
        HealCascade cascade = cascadeWith(failingOllama(ollamaCalls), cursor);

        HealResult result = cascade.heal("TC_ABSENT",
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.TYPE_FIELD, "Enter 12345 in the Postal code field"),
                FORM, new byte[]{1}, "bind failed", null, true);

        Assert.assertFalse(result.ok());
        Assert.assertEquals(ollamaCalls.get(), 0,
                "nothing on this page is a postal code, so the row picker cannot answer");
    }
}
