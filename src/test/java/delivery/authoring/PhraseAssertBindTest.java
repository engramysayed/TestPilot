package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.util.List;

/** Text-phrase asserts bind to body textContains — never AMBIGUOUS on chrome candidates. */
public class PhraseAssertBindTest {

    @Test
    public void confirmTheTextUsesBodyXpathDespiteChromeCandidates() {
        String html = """
                <html><body>
                  <a href="/upstream"><img alt="Project banner"></a>
                  <div id="main-column">
                    <h3>Choice List</h3>
                    <select id="choice"><option>One</option></select>
                  </div>
                </body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(html, 80000));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the text Choice List is visible");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "textContains");
        Assert.assertEquals(r.steps().get(0).assertionExpected(), "Choice List");
        Assert.assertEquals(r.steps().get(0).locatorStrategy(), "xpath");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("Choice List"));
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("//body"));
    }

    @Test
    public void hiddenPhraseStillBindsBodyXpath() {
        String html = """
                <html><body>
                  <div id="start"><button>Start</button></div>
                  <div id="finish" style="display:none"><h4>Hello World!</h4></div>
                </body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(html, 80000));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                "Confirm the text Hello World! is visible");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "textContains");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("Hello World!"));
    }

    @Test
    public void controlVisibleAssertIsUnchanged() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "logout", "a", "Logout"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the Logout button is visible");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "visible");
        Assert.assertEquals(r.steps().get(0).locatorValue(), "logout");
    }
}
