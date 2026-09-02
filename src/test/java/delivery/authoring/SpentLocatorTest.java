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
