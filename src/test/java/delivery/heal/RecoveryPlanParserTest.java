package delivery.heal;

import delivery.authoring.LocatorValidator;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RecoveryPlanParserTest {

    private static final String HTML = """
            <form>
              <input id="email" name="email" value="filled@example.com" />
              <button id="login" name="login">Log in</button>
            </form>
            """;

    @Test
    public void parsesValidRecoveryPlan() {
        String raw = """
                {"mode":"recovery","thought":"Email is filled",
                "recoverySteps":[
                  {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""},
                  {"action":"click","locatorStrategy":"id","locatorValue":"login","value":""}
                ],
                "automationNotes":["Keep Leave-empty blank on Excel step 2"]}
                """;
        RecoveryPlanParser.RecoveryPlan plan = new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML)
                .orElseThrow();

        Assert.assertEquals(plan.steps().size(), 2);
        Assert.assertEquals(plan.steps().get(0).action(), "clear");
        Assert.assertEquals(plan.steps().get(0).rationale(), "heal:recovery");
        Assert.assertEquals(plan.automationNotes().size(), 1);
        Assert.assertTrue(plan.thought().contains("Email"));
    }

    @Test
    public void rejectsLocatorAbsentFromPage() {
        String raw = """
                {"mode":"recovery","thought":"wrong","recoverySteps":[
                  {"action":"clear","locatorStrategy":"id","locatorValue":"imagined","value":""}
                ]}
                """;
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML)
                .isEmpty());
    }

    @Test
    public void rejectsDisallowedAction() {
        String raw = """
                {"mode":"recovery","recoverySteps":[
                  {"action":"navigate","locatorStrategy":"css","locatorValue":"input[name='email']","value":"/x"}
                ]}
                """;
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML)
                .isEmpty());
    }

    @Test
    public void acceptsDesignSpecLocatorShape() {
        String raw = """
                {"mode":"recovery","recoverySteps":[
                  {"action":"clear","strategy":"css","value":"input[name='email']"}
                ]}
                """;
        List<ProvenStep> steps = new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML)
                .orElseThrow()
                .steps();
        Assert.assertEquals(steps.size(), 1);
        Assert.assertEquals(steps.get(0).locatorValue(), "input[name='email']");
    }

    @Test
    public void rejectsPartialPlanWhenAnyStepInvalid() {
        String raw = """
                {"mode":"recovery","thought":"mixed",
                "recoverySteps":[
                  {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""},
                  {"action":"clear","locatorStrategy":"id","locatorValue":"imagined","value":""}
                ]}
                """;
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML)
                .isEmpty(), "partial recovery must be rejected all-or-nothing");
    }

    @Test
    public void rejectsWhenMoreThanMaxSteps() {
        String raw = """
                {"mode":"recovery","recoverySteps":[
                  {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""},
                  {"action":"click","locatorStrategy":"id","locatorValue":"login","value":""},
                  {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""},
                  {"action":"click","locatorStrategy":"id","locatorValue":"login","value":""},
                  {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""},
                  {"action":"click","locatorStrategy":"id","locatorValue":"login","value":""}
                ]}
                """;
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML)
                .isEmpty());
    }

    @Test
    public void acceptsNavigateOnExcelOpenPath() {
        String raw = """
                {"mode":"recovery","thought":"left login",
                "recoverySteps":[
                  {"action":"navigate","value":"/login"}
                ]}
                """;
        RecoveryPlanParser.RecoveryPlan plan = new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML, null, "/login")
                .orElseThrow();
        Assert.assertEquals(plan.steps().get(0).action(), "navigate");
        Assert.assertEquals(plan.steps().get(0).value(), "/login");
    }

    @Test
    public void rejectsNavigateOffExcelOpenPath() {
        String raw = """
                {"mode":"recovery","recoverySteps":[
                  {"action":"navigate","value":"https://evil.example/"}
                ]}
                """;
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, HTML, null, "/login")
                .isEmpty());
    }

    @Test
    public void acceptsLocatorMissingFromSlimButPresentInFullHtml() {
        String slim = "<form><button id='login'>Log in</button></form>";
        String full = """
                <form>
                  <input id="email" name="email" value="filled@example.com" />
                  <button id="login">Log in</button>
                </form>
                """;
        String raw = """
                {"mode":"recovery","recoverySteps":[
                  {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""}
                ]}
                """;
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", raw, slim, full)
                .isPresent());
    }

    @Test
    public void ignoresNonRecoveryJson() {
        Assert.assertTrue(new RecoveryPlanParser(new LocatorValidator())
                .parse("TC1", "{\"steps\":[]}", HTML)
                .isEmpty());
    }
}
