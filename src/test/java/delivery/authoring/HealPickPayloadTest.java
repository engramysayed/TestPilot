package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class HealPickPayloadTest {

    @Test
    public void scoredTableIncludesTotals() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Settings");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "id", "settings-primary", "button", "Settings"),
                new DomCandidate("c2", "id", "settings-advanced", "button", "Settings")
        );
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        HealPickPayload p = HealPickPayload.from(intent, d, "<html></html>");
        Assert.assertTrue(p.whyTied().startsWith("TIE"), p.whyTied());
        Assert.assertTrue(p.whyTied().contains("same-label=Settings") || p.whyTied().contains("margin="),
                p.whyTied());
        Assert.assertTrue(p.scoredTable().contains("c1") && p.scoredTable().contains("total="),
                p.scoredTable());
        Assert.assertTrue(p.scoredTable().contains("stability=")
                        && p.scoredTable().contains("intentMatch="),
                p.scoredTable());
        Assert.assertEquals(p.screenshotPath(), "");
        Assert.assertFalse(p.ollamaSystem().toLowerCase().contains("invent"));
        Assert.assertFalse(p.ollamaSystem().toLowerCase().contains("thought"));
        String user = p.ollamaUser("TC_01", "tie");
        Assert.assertTrue(user.contains("Why tied:"), user);
        Assert.assertTrue(user.contains("Neighborhood"), user);
        Assert.assertTrue(user.contains(p.whyTied()), user);
    }

    @Test
    public void screenshotPathOptionalEmptyOk() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Settings");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "id", "settings-primary", "button", "Settings"),
                new DomCandidate("c2", "id", "settings-advanced", "button", "Settings")
        );
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        HealPickPayload p = HealPickPayload.from(intent, d, "<html></html>")
                .withScreenshotPath("D:\\evidence\\tie.png");
        Assert.assertEquals(p.screenshotPath(), "D:\\evidence\\tie.png");
        Assert.assertTrue(p.ollamaUser("TC_01", "tie").contains("D:\\evidence\\tie.png"));
        Assert.assertEquals(p.withScreenshotPath(null).screenshotPath(), "");
    }

    @Test
    public void neighborhoodIsParentNotWholePage() {
        String html = """
                <html><body>
                <nav><span data-axis-test-id="side-nav-support-submenu-button">Support</span>
                <a data-axis-test-id="side-nav-home-link">Home</a></nav>
                <main id="huge">""" + "x".repeat(4000) + """
                </main></body></html>
                """;
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        HealPickPayload p = HealPickPayload.from(intent, d, html);
        Assert.assertTrue(p.neighborhood().length() <= HealPickPayload.NEIGHBORHOOD_MAX + 80,
                String.valueOf(p.neighborhood().length()));
        Assert.assertFalse(p.neighborhood().contains("x".repeat(200)));
        Assert.assertTrue(p.neighborhood().contains("Support")
                        || p.neighborhood().contains("side-nav-support"),
                p.neighborhood());
    }

    @Test
    public void emptyDecisionHasNoNeighborhoodCard() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Warp Drive");
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, List.of(
                new DomCandidate("c1", "id", "cancel", "button", "Cancel"))));
        Assert.assertEquals(d.kind(), BindDecision.Kind.EMPTY);
        HealPickPayload p = HealPickPayload.from(intent, d, "<html><body>lots</body></html>");
        Assert.assertEquals(p.scoredTable(), "");
        Assert.assertEquals(p.neighborhood(), "");
    }
}