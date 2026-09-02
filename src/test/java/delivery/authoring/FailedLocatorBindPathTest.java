package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.heal.FailedLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Locale;

/** First-bind path must exclude locators already failed earlier in the TC. */
public class FailedLocatorBindPathTest {

    private static final String UPSTREAM_HTML = """
            <html><body>
              <a href="/upstream">banner</a>
              <button>Add Item</button>
            </body></html>
            """;

    private static final String HREF_ONLY_HTML = """
            <html><body>
              <a href="/upstream">x</a>
            </body></html>
            """;

    @Test
    public void authorIntent_leavesUnboundWhenOnlyHrefTwinsFailed() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click upstream link");
        List<FailedLocator> failed = List.of(
                new FailedLocator("css", "a[href='/upstream']", "zero size"));
        AuthoringService service = new AuthoringService(
                new LocalLlmClient("", ""), new LocatorValidator());
        List<ProvenStep> steps = service.authorIntent(
                "TC", intent, HREF_ONLY_HTML, null, false, List.of(), failed);
        Assert.assertFalse(steps.isEmpty());
        Assert.assertFalse(steps.get(0).validated(),
                "href twins banned — must not bind on first-bind path");
    }

    @Test
    public void authorIntent_doesNotReselectFailedCssXpathTwin() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click banner");
        List<FailedLocator> failed = List.of(
                new FailedLocator("css", "a[href='/upstream']", "zero size"));
        AuthoringService service = new AuthoringService(
                new LocalLlmClient("", ""), new LocatorValidator());
        List<ProvenStep> steps = service.authorIntent(
                "TC", intent, UPSTREAM_HTML, null, false, List.of(), failed);
        Assert.assertFalse(steps.isEmpty(), "expected a bind attempt");
        ProvenStep step = steps.get(0);
        Assert.assertTrue(step.validated(), step.rationale());
        String loc = step.locatorValue() == null ? "" : step.locatorValue();
        Assert.assertFalse(isHrefUpstreamTwin(loc),
                "must not reselect failed css+xpath href twin: " + loc);
    }

    private static boolean isHrefUpstreamTwin(String locator) {
        String n = locator.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("\"", "");
        return n.contains("href=/upstream") || n.contains("@href=/upstream");
    }

    @Test
    public void authorIntent_laterIntentCanStillBindOtherControls() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Add Item");
        List<FailedLocator> failed = List.of(
                new FailedLocator("css", "a[href='/upstream']", "zero size"));
        AuthoringService service = new AuthoringService(
                new LocalLlmClient("", ""), new LocatorValidator());
        List<ProvenStep> steps = service.authorIntent(
                "TC", intent, UPSTREAM_HTML, null, false, List.of(), failed);
        Assert.assertFalse(steps.isEmpty());
        ProvenStep step = steps.get(0);
        Assert.assertTrue(step.validated(), step.rationale());
        String loc = step.locatorValue() == null ? "" : step.locatorValue();
        Assert.assertFalse(loc.contains("/upstream"), loc);
        Assert.assertTrue(loc.contains("Add Item") || loc.contains("Add"),
                loc);
    }
}
