package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Modern dropdowns are divs wearing ARIA roles. They have to reach the candidate pool, keep their
 * identity as dropdowns, and be told apart from their identical siblings.
 */
public class AriaWidgetExtractionTest {

    private static final String DATE_OF_BIRTH_FORM = """
            <form>
              <div role="combobox" aria-label="Day">Day</div>
              <div role="combobox" aria-label="Month">Month</div>
              <div role="combobox" aria-label="Year">Year</div>
            </form>
            """;

    @Test
    public void ariaComboboxBecomesACandidate() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(DATE_OF_BIRTH_FORM);
        Assert.assertTrue(
                candidates.stream().anyMatch(c -> c.value().contains("aria-label='Day'")),
                "the Day combobox must be reachable: " + candidates);
    }

    @Test
    public void comboboxReportsItselfAsADropdownNotADiv() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(DATE_OF_BIRTH_FORM);
        Assert.assertTrue(
                candidates.stream()
                        .filter(c -> c.value().contains("aria-label='Day'"))
                        .allMatch(c -> "combobox".equals(c.tag())),
                "kind should describe the widget, not the tag: " + candidates);
    }

    @Test
    public void selectIntentOnAComboboxProducesASelectAction() {
        ManualTestCase tc = new ManualTestCase(
                "TC_DOB", "Register", "",
                "1. Select 15 from the Day dropdown",
                "Day is 15", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(
                tc, DomCandidateExtractor.extract(DATE_OF_BIRTH_FORM));

        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).action(), "select",
                "a combobox must never be typed into");
        Assert.assertEquals(r.steps().get(0).value(), "15");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("Day"),
                r.steps().get(0).locatorValue());
    }

    @Test
    public void identicalWidgetsAreSeparatedByASecondAttribute() {
        String html = """
                <div role="combobox" title="Pick one">A</div>
                <div role="combobox" title="Pick two">B</div>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        List<String> comboboxes = candidates.stream()
                .filter(c -> "css".equals(c.strategy()) && "combobox".equals(c.tag()))
                .map(DomCandidate::value)
                .distinct()
                .toList();
        Assert.assertEquals(comboboxes.size(), 2, "both widgets need their own selector: " + candidates);
        Assert.assertTrue(comboboxes.stream().noneMatch(v -> v.equals("div[role='combobox']")),
                "the shared role alone cannot identify either: " + comboboxes);
    }

    @Test
    public void twoAttributeSelectorPassesValidation() {
        LocatorValidator validator = new LocatorValidator();
        Assert.assertTrue(validator.validate(new LocatorCandidate(
                "css", "div[role='combobox'][aria-label='Day']", "Page", "")).valid());
        Assert.assertTrue(validator.validate(new LocatorCandidate(
                "xpath", "//div[@role='combobox' and @aria-label='Day']", "Page", "")).valid());
    }
}
