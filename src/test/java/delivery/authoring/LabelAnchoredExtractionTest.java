package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Standard accessible markup puts the field name in a sibling node and leaves the control itself
 * anonymous. Reading attributes only, such a field never reaches the candidate table, so binding
 * fails and every heal tier then picks from rows that cannot contain the answer.
 */
public class LabelAnchoredExtractionTest {

    /** Wrapping label with a framework-generated id — the shape that produced the false PASS. */
    private static final String WRAPPING_LABEL = """
            <html><body>
              <form>
                <label><input type="text" id="_r_3_"><span>First name</span></label>
                <label><input type="text" id="_r_4_"><span>Surname</span></label>
                <div aria-label="Back" role="button">Back</div>
              </form>
            </body></html>
            """;

    private static final String LABEL_FOR = """
            <html><body>
              <form>
                <label for=":r7:">Email address</label>
                <input type="text" id=":r7:">
              </form>
            </body></html>
            """;

    @Test
    public void wrappingLabelMakesAnAnonymousInputAddressable() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(WRAPPING_LABEL);

        DomCandidate first = candidates.stream()
                .filter(c -> c.label().toLowerCase().contains("first name"))
                .findFirst()
                .orElse(null);

        assertTrue(first != null, "First name input must be a candidate, got: " + candidates);
        assertEquals(first.value(), "//label[normalize-space(.)='First name']//input");
        assertFalse(first.value().contains("_r_3_"), "must not lean on the generated id");
    }

    @Test
    public void siblingLabelAnchorsOnFollowingControl() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(LABEL_FOR);

        DomCandidate email = candidates.stream()
                .filter(c -> c.label().toLowerCase().contains("email"))
                .findFirst()
                .orElse(null);

        assertTrue(email != null, "Email input must be a candidate, got: " + candidates);
        assertEquals(email.value(),
                "//input[@id=//label[normalize-space(.)='Email address']/@for]");
    }

    @Test
    public void followingAxisIsNotEmittedWhenTheNextInputIsADifferentField() {
        String html = """
                <html><body><form>
                  <label for=":fn:">First name</label>
                  <label for=":em:">Email</label>
                  <input id=":em:" type="text">
                  <input id=":fn:" type="text">
                </form></body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        DomCandidate first = candidates.stream()
                .filter(c -> c.label().toLowerCase().contains("first name"))
                .findFirst()
                .orElse(null);
        assertTrue(first != null, "First name must be a candidate, got: " + candidates);
        assertEquals(first.value(),
                "//input[@id=//label[normalize-space(.)='First name']/@for]");
        assertFalse(first.value().contains("following::"),
                "document-order following:: would hit Email's input, got " + first.value());
    }

    @Test
    public void anonymousControlKeepsItsNameFromTheAutocompleteToken() {
        String html = """
                <html><body><form>
                  <input type="text" autocomplete="family-name">
                </form></body></html>
                """;

        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        assertTrue(candidates.stream().anyMatch(c -> c.label().contains("surname")),
                "autocomplete token should name the field, got: " + candidates);
    }

    @Test
    public void aTypeIntentBindsToTheLabelledField() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(WRAPPING_LABEL);

        ManualTestCase tc = new ManualTestCase(
                "TC_LABEL", "Register", "",
                "1. Enter Ahmed in the First name field",
                "Value accepted", "P1", "");

        StepIntentBinder.BindResult result = StepIntentBinder.bind(tc, candidates);

        assertTrue(result.ok(), "expected a bind, got: " + result.rejectReason());
        assertEquals(result.steps().get(0).locatorValue(),
                "//label[normalize-space(.)='First name']//input");
        assertEquals(result.steps().get(0).value(), "Ahmed");
    }

    @Test
    public void anAssertIntentCannotLandOnAnUnrelatedControl() {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(WRAPPING_LABEL);

        DomCandidate back = candidates.stream()
                .filter(c -> c.label().toLowerCase().contains("back"))
                .findFirst()
                .orElseThrow();

        assertFalse(StepIntentBinder.candidateSharesFieldToken(
                        "Confirm the First name field is visible", back),
                "the Back button must never satisfy a First name assertion");
    }

    @Test
    public void aFieldAssertDoesNotBindToAShowHideToggle() {
        DomCandidate toggle = new DomCandidate(
                "c19", "css", "div[aria-label='Hide password']", "div", "Hide password");
        DomCandidate field = new DomCandidate(
                "c4", "xpath", "//label[normalize-space(.)='Password']//input", "input", "Password");

        assertFalse(StepIntentBinder.candidateSharesFieldToken(
                        "Confirm the Password field is visible", toggle),
                "a show/hide control is not the field");
        assertTrue(StepIntentBinder.candidateSharesFieldToken(
                "Confirm the Password field is visible", field));
    }

    @Test
    public void bindPrefersThePasswordInputOverTheHideToggle() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c19", "css", "div[aria-label='Hide password']", "div", "Hide password"),
                new DomCandidate("c4", "css", "input[type='password']", "input", "Password"));
        ManualTestCase tc = new ManualTestCase(
                "TC_PW", "Register", "No login required.",
                "1. Confirm the Password field is visible",
                "Password shown", "P1", "");

        StepIntentBinder.BindResult result = StepIntentBinder.bind(tc, candidates);
        assertTrue(result.ok(), result.rejectReason());
        assertEquals(result.steps().get(0).locatorValue(), "input[type='password']");
    }

    @Test
    public void labelAnchoredLocatorSurvivesValidationAndPresence() {
        String locator = "//label[normalize-space(.)='First name']//input";

        LocatorValidator.ValidationResult result = new LocatorValidator().validate(
                new LocatorCandidate("xpath", locator, "", ""));

        assertTrue(result.valid(), "validator rejected: " + result.reason());
        assertTrue(HtmlLocatorPresence.present("xpath", locator, WRAPPING_LABEL));
        assertFalse(HtmlLocatorPresence.present("xpath",
                "//label[normalize-space(.)='Middle name']//input", WRAPPING_LABEL),
                "a label that is not on the page must be rejected");
    }
}
