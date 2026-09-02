package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Customer TestData must survive heal candidate forcing — invent must not replace a concrete line.
 */
public class TestDataHealPriorityTest {

    @Test
    public void columnConcreteBeatsInventAndStepPlaceholder() {
        Assert.assertEquals(
                DummyValueInventor.fromStepOrInvent(
                        "Enter in the First Name field", "John",
                        "input", "text", "first-name", "First Name", ""),
                "John");
    }

    @Test
    public void columnConcreteBeatsEnterLiteralWhenBothPresent() {
        Assert.assertEquals(
                DummyValueInventor.fromStepOrInvent(
                        "Enter Alice in the First Name field", "John",
                        "input", "text", "first-name", "First Name", ""),
                "John");
    }

    @Test
    public void selectInStepStillWinsOverMisalignedColumn() {
        Assert.assertEquals(
                DummyValueInventor.fromStepOrInvent(
                        "Select 1995 from the Year dropdown",
                        "nora@example.com",
                        "select", "text", "year", "Year", ""),
                "1995");
    }

    @Test
    public void healPreferringCandidateUsesIntentTestData() {
        String html = """
                <body>
                  <input id="first-name" name="firstName" data-test="firstName" placeholder="First Name"/>
                </body>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        DomCandidate first = candidates.stream()
                .filter(c -> "first-name".equals(c.value()) || "firstName".equals(c.value()))
                .findFirst()
                .orElseThrow();
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD,
                "Enter in the First Name field",
                "John");
        List<ProvenStep> steps = authoring.stepsPreferringCandidate(
                "TC_TD", intent, candidates, first.id(), false);
        Assert.assertFalse(steps.isEmpty());
        Assert.assertEquals(steps.get(0).value(), "John",
                "heal must type Excel TestData, got " + steps.get(0).value());
    }
}
