package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.util.List;

/**
 * Adjacent accessible names must be captions, not layout columns.
 * Fixtures are synthetic layouts — no real-site hosts or brands.
 */
public class AccessibleNameCaptionTest {

    private static final String BANNER_BESIDE_CONTENT = """
            <html><body>
              <a href="/upstream"><img alt="Project banner"></a>
              <div id="main-column">
                <h3>Items</h3>
                <button onclick="addItem()">Add Item</button>
              </div>
              <div id="page-end">Powered by Example Labs</div>
            </body></html>
            """;

    @Test
    public void bannerLinkDoesNotInheritContentColumnText() {
        String slim = HtmlSlimmer.slim(BANNER_BESIDE_CONTENT, 80000);
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slim);
        DomCandidate banner = candidates.stream()
                .filter(c -> c.value() != null && c.value().contains("/upstream"))
                .findFirst()
                .orElseThrow();
        String hay = (banner.label() + " " + banner.value()).toLowerCase();
        Assert.assertFalse(hay.contains("add item"),
                "layout sibling must not name the banner, got: " + banner);
    }

    @Test
    public void clickAddItemBindsTheButtonNotTheBanner() {
        String slim = HtmlSlimmer.slim(BANNER_BESIDE_CONTENT, 80000);
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slim);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Add Item button");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().toLowerCase().contains("add item"));
        Assert.assertFalse(r.steps().get(0).locatorValue().contains("/upstream"));
    }

    @Test
    public void shortDivCaptionStillNamesAnAnonymousInput() {
        String html = """
                <html><body><form>
                  <div>Given name</div>
                  <input type="text">
                </form></body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        Assert.assertTrue(candidates.stream().anyMatch(c ->
                        c.label().toLowerCase().contains("given name")),
                "leaf caption div must still name the input, got: " + candidates);
    }
}
