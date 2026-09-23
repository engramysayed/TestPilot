package delivery.authoring;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.util.List;

/**
 * Button/link accessible names come from the rendered subtree, not the id.
 * Bindings stay generic — no host-specific pay-later aliases.
 */
public class AccessibleNameButtonContentsTest {

    private static final String ORIGINAL_PAY_LATER = """
            <html><body>
              <p>Payment: simulated only. Do not enter a card.</p>
              <button id="pay-simulated" type="button">Pay later (simulated)</button>
            </body></html>
            """;

    @Test
    public void accessibleNameUsesButtonSubtreeNotTheId() {
        Document doc = Jsoup.parse(ORIGINAL_PAY_LATER);
        Element button = doc.getElementById("pay-simulated");
        Assert.assertEquals(AccessibleName.of(button), "Pay later (simulated)");
    }

    @Test
    public void originalPayLaterSimulatedBindsTheUnrelatedHyphenatedId() {
        StepIntentBinder.BindResult bound = bindClick(
                ORIGINAL_PAY_LATER, "Click Pay later (simulated)");
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(
                bound.steps().get(0).locatorValue().toLowerCase().contains("pay-simulated"),
                bound.steps().get(0).locatorValue());
    }

    @Test
    public void nestedButtonTextIsTheAccessibleName() {
        String html = """
                <html><body>
                  <button id="pay-simulated" type="button">
                    <span class="label"><em>Pay later (simulated)</em></span>
                  </button>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        Assert.assertEquals(
                AccessibleName.of(doc.getElementById("pay-simulated")),
                "Pay later (simulated)");
        StepIntentBinder.BindResult bound = bindClick(html, "Click Pay later (simulated)");
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(
                bound.steps().get(0).locatorValue().toLowerCase().contains("pay-simulated"),
                bound.steps().get(0).locatorValue());
    }

    @Test
    public void unrelatedIdStillBindsFromVisibleText() {
        String html = """
                <html><body>
                  <button id="ctrl-9" type="button">Pay later (simulated)</button>
                </body></html>
                """;
        StepIntentBinder.BindResult bound = bindClick(html, "Click Pay later (simulated)");
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(
                bound.steps().get(0).locatorValue().toLowerCase().contains("ctrl-9"),
                bound.steps().get(0).locatorValue());
    }

    @Test
    public void parentheticalVisibleTextMatchesTheInstruction() {
        String html = """
                <html><body>
                  <button id="job-retry" type="button">Retry (offline)</button>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        Assert.assertEquals(AccessibleName.of(doc.getElementById("job-retry")), "Retry (offline)");
        StepIntentBinder.BindResult bound = bindClick(html, "Click Retry (offline)");
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(
                bound.steps().get(0).locatorValue().toLowerCase().contains("job-retry"),
                bound.steps().get(0).locatorValue());
    }

    @Test
    public void duplicateVisibleLabelsStayAmbiguous() {
        String html = """
                <html><body>
                  <button id="pay-primary" type="button">Pay later (simulated)</button>
                  <button id="pay-backup" type="button">Pay later (simulated)</button>
                </body></html>
                """;
        StepIntentBinder.BindResult bound = bindClick(html, "Click Pay later (simulated)");
        Assert.assertFalse(bound.ok(), "duplicate labels must not silently pick a winner");
        Assert.assertTrue(bound.rejectReason().startsWith("AMBIGUOUS:"), bound.rejectReason());
    }

    @Test
    public void misleadingNearbyControlDoesNotStealTheNamedButton() {
        String html = """
                <html><body>
                  <p>Pay later (simulated)</p>
                  <button class="icon-bag" type="button"></button>
                  <button id="place-order" type="submit">Place order</button>
                  <button id="pay-simulated" type="button">Pay later (simulated)</button>
                </body></html>
                """;
        StepIntentBinder.BindResult bound = bindClick(html, "Click Pay later (simulated)");
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(
                bound.steps().get(0).locatorValue().toLowerCase().contains("pay-simulated"),
                bound.steps().get(0).locatorValue());
        Assert.assertFalse(
                bound.steps().get(0).locatorValue().toLowerCase().contains("place-order"),
                bound.steps().get(0).locatorValue());
    }

    private static StepIntentBinder.BindResult bindClick(String html, String instruction) {
        String slim = HtmlSlimmer.slim(html, 80000);
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slim);
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, instruction);
        return StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
    }
}
