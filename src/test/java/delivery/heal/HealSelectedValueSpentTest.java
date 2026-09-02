package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * "Confirm Option 2 is the selected value" must reuse the dropdown this TC just selected.
 * Spent-locator rejection is for later fills, not for selected/checked state asserts.
 */
public class HealSelectedValueSpentTest {

    private static final String HTML = """
            <body>
              <select id="choice">
                <option value="1">Option 1</option>
                <option value="2" selected>Option 2</option>
              </select>
            </body>
            """;

    @Test
    public void selectedValueHealMayReuseTheDropdownJustFilled() {
        LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "x") {
            @Override
            public String completeJson(String system, String user) {
                throw new RuntimeException("ollama should be skippable");
            }
        };
        List<DomCandidate> all = DomCandidateExtractor.extract(HTML);
        DomCandidate select = all.stream()
                .filter(c -> "id".equals(c.strategy()) && "choice".equals(c.value()))
                .findFirst()
                .orElseThrow();
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String solve(String intentText, String failureReason, String shortlistTable,
                                String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                List<String> priorSteps) {
                return "{\"candidateId\":\"" + select.id() + "\"}";
            }
        };
        List<ProvenStep> spent = List.of(new ProvenStep(
                "TC", "Page", "elementAction", "select",
                "id", "choice", "Option 2", "", "", true, "prior"));
        HealCascade cascade = new HealCascade(
                new AuthoringService(llm, new LocatorValidator()), cursor);
        HealResult result = cascade.heal(
                "TC",
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                        "Confirm Option 2 is the selected value"),
                HTML, new byte[]{1}, "bind failed", null, true,
                List.of("select id choice = Option 2"), true, spent);

        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.steps().get(0).assertionType(), "selected");
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("choice"));
    }
}
