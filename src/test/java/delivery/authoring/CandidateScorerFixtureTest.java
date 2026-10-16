package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CandidateScorerFixtureTest {

    @Test
    public void supportClick_isClearHook() {
        String html = readFixture();
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        Assert.assertEquals(d.kind(), BindDecision.Kind.CLEAR, d.toString());
        Assert.assertTrue(d.winner().value().contains("side-nav-support-submenu-button"),
                d.winner().toString());
    }

    @Test
    public void saveClick_prefersHookOverId() {
        String html = readFixture();
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC_01", cs, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("save-hook")
                        || "data-testid".equals(r.steps().get(0).locatorStrategy()),
                r.steps().get(0).locatorStrategy() + "=" + r.steps().get(0).locatorValue());
    }

    @Test
    public void emberId_isNotTheRefreshLocator() {
        String html = readFixture();
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Refresh");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC_01", cs, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertFalse("ember1234".equals(r.steps().get(0).locatorValue()));
    }

    @Test
    public void twoDeletes_areTied() {
        BindDecision d = decide("Click Delete");
        Assert.assertEquals(d.kind(), BindDecision.Kind.TIE, d.toString());
    }

    @Test
    public void twoSettings_areTied() {
        BindDecision d = decide("Click Settings");
        Assert.assertEquals(d.kind(), BindDecision.Kind.TIE, d.toString());
        Assert.assertTrue(d.shortlist().size() >= 2);
    }

    private static BindDecision decide(String step) {
        String html = readFixture();
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, step);
        return CandidateScorer.decide(CandidateScorer.score(intent, cs));
    }

    private static String readFixture() {
        try (InputStream in = CandidateScorerFixtureTest.class
                .getResourceAsStream("/locator-fixtures/scorer-sidebar.html")) {
            Assert.assertNotNull(in, "classpath /locator-fixtures/scorer-sidebar.html missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}