package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CheckboxBindAndAssertTest {
    private static final String CHECKBOX_HTML = """
            <html><body>
            <form id="checkboxes">
              <input type="checkbox"> checkbox 1<br>
              <input type="checkbox" checked> checkbox 2
            </form>
            <button>Add Element</button>
            <div id="elements"></div>
            </body></html>
            """;

    @Test
    public void extractorEmitsIndexedCheckboxCandidates() {
        List<DomCandidate> c = DomCandidateExtractor.extract(CHECKBOX_HTML);
        Assert.assertTrue(c.stream().anyMatch(x ->
                "xpath".equals(x.strategy())
                        && "(//input[@type='checkbox'])[1]".equals(x.value())),
                "expected xpath for checkbox 1");
        Assert.assertTrue(c.stream().anyMatch(x ->
                "xpath".equals(x.strategy())
                        && "(//input[@type='checkbox'])[2]".equals(x.value())),
                "expected xpath for checkbox 2");
        Assert.assertTrue(c.stream().noneMatch(x ->
                "css".equals(x.strategy()) && x.value().contains("nth-of-type")),
                "must not emit nth-of-type css for checkboxes: "
                        + DomCandidateExtractor.formatTable(c));
    }

    @Test
    public void bindsClickToNthCheckboxNotForm() {
        List<DomCandidate> c = DomCandidateExtractor.extract(CHECKBOX_HTML);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click checkbox 1 to check it");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC1", c, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        ProvenStep step = r.steps().get(0);
        Assert.assertEquals(step.action(), "click");
        Assert.assertTrue(step.locatorValue().contains("[1]"), step.locatorValue());
        Assert.assertFalse("checkboxes".equals(step.locatorValue()));
    }

    @Test
    public void uncheckedAssertUsesStateNotTextContains() {
        List<DomCandidate> c = DomCandidateExtractor.extract(CHECKBOX_HTML);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm checkbox 1 is unchecked");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC1", c, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        ProvenStep step = r.steps().get(0);
        Assert.assertEquals(step.assertionType(), "unchecked");
        Assert.assertTrue(step.locatorValue().contains("[1]"), step.locatorValue());
    }

    @Test
    public void secureAreaShownIsVisibleNotTextContainsPhrase() {
        Assert.assertNull(StepIntentBinder.extractAssertTextPhrase(
                "Confirm the Secure Area page is shown"));
        Assert.assertNull(StepIntentBinder.extractStateAssertion(
                "Confirm the Secure Area page is shown"));
    }

    @Test
    public void helloWorldStillExtractsAsTextPhrase() {
        Assert.assertEquals(
                StepIntentBinder.extractAssertTextPhrase("Confirm the text Hello World! is visible"),
                "Hello World!");
    }

    @Test
    public void selectedValuePhraseFromConfirm() {
        Assert.assertEquals(StepIntentBinder.extractStateAssertion(
                "Confirm Option 2 is the selected value"), "selected");
        Assert.assertEquals(StepIntentBinder.extractSelectedValuePhrase(
                "Confirm Option 2 is the selected value"), "Option 2");
    }

    @Test
    public void addElementPrefersButtonOverContainer() {
        List<DomCandidate> c = DomCandidateExtractor.extract(CHECKBOX_HTML);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Add Element button");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC2", c, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        ProvenStep step = r.steps().get(0);
        Assert.assertFalse("elements".equals(step.locatorValue()), step.locatorValue());
        Assert.assertTrue(
                step.locatorValue().toLowerCase().contains("add")
                        || "button".equalsIgnoreCase(
                        c.stream().filter(x -> x.value().equals(step.locatorValue()))
                                .map(DomCandidate::tag).findFirst().orElse("")),
                step.locatorStrategy() + ":" + step.locatorValue());
    }

    @Test
    public void fullCaseBindsDistinctCheckboxes() {
        ManualTestCase tc = new ManualTestCase(
                "TC_TI_03", "Toggle", "",
                """
                        1. Confirm checkbox 1 is unchecked
                        2. Click checkbox 1 to check it
                        3. Confirm checkbox 1 is checked
                        4. Click checkbox 2 to uncheck it
                        5. Confirm checkbox 2 is unchecked
                        """,
                "ok", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, DomCandidateExtractor.extract(CHECKBOX_HTML));
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().size(), 5);
        Assert.assertEquals(r.steps().get(0).assertionType(), "unchecked");
        Assert.assertEquals(r.steps().get(1).action(), "click");
        Assert.assertTrue(r.steps().get(1).locatorValue().contains("checkbox"), r.steps().get(1).locatorValue());
        Assert.assertEquals(r.steps().get(2).assertionType(), "checked");
        Assert.assertEquals(r.steps().get(3).action(), "click");
        Assert.assertTrue(r.steps().get(3).locatorValue().contains("checkbox"), r.steps().get(3).locatorValue());
        Assert.assertEquals(r.steps().get(4).assertionType(), "unchecked");
        // Distinct ordinals for checkbox 1 vs 2
        Assert.assertNotEquals(r.steps().get(1).locatorValue(), r.steps().get(3).locatorValue());
    }
}
