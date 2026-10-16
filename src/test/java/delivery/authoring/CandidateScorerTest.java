package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CandidateScorerTest {

    @Test
    public void preferredHookOutranksId() {
        DomCandidate hook = new DomCandidate("c1", "css",
                "button[data-axis-test-id='save-hook']", "button", "Save");
        DomCandidate id = new DomCandidate("c2", "id", "btn-save", "button", "Save");
        Assert.assertTrue(CandidateScorer.stability(hook) > CandidateScorer.stability(id),
                hook + " vs " + id);
    }

    @Test
    public void testIdOutranksRawId() {
        DomCandidate testid = new DomCandidate("c1", "data-testid", "save", "button", "Save");
        DomCandidate id = new DomCandidate("c2", "id", "btn-save", "button", "Save");
        Assert.assertTrue(CandidateScorer.stability(testid) > CandidateScorer.stability(id));
    }

    @Test
    public void generatedIdScoresZero() {
        DomCandidate ember = new DomCandidate("c1", "id", "ember1234", "button", "Refresh");
        Assert.assertEquals(CandidateScorer.stability(ember), 0);
    }

    @Test
    public void textXpathBelowStableId() {
        DomCandidate id = new DomCandidate("c1", "id", "save-changes", "button", "Save");
        DomCandidate xp = new DomCandidate("c2", "xpath",
                "//button[normalize-space()='Save']", "button", "Save");
        Assert.assertTrue(CandidateScorer.stability(id) > CandidateScorer.stability(xp));
    }

    @Test
    public void quotedNameBeatsSharedActionToken() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "css", "span[data-axis-test-id='side-nav-support-submenu-button']",
                        "span", "Support"),
                new DomCandidate("c2", "css", "a[data-axis-test-id='side-nav-home-link']",
                        "a", "Home")
        );
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        Assert.assertEquals(d.kind(), BindDecision.Kind.CLEAR);
        Assert.assertTrue(d.winner().value().contains("support"));
    }

    @Test
    public void twinSameNameIsTie() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Settings");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "id", "settings-primary", "button", "Settings"),
                new DomCandidate("c2", "id", "settings-advanced", "button", "Settings")
        );
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        Assert.assertEquals(d.kind(), BindDecision.Kind.TIE);
        Assert.assertEquals(d.shortlist().size(), 2);
        Assert.assertNull(d.winner());
    }

    @Test
    public void locatorTwinsAreClear() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Save");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "css", "#save-btn", "button", "Save"),
                new DomCandidate("c2", "xpath", "//button[@id='save-btn']", "button", "Save")
        );
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        Assert.assertEquals(d.kind(), BindDecision.Kind.CLEAR, d.toString());
    }

    @Test
    public void emptyWhenNothingMatches() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Warp Drive");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "id", "cancel", "button", "Cancel")
        );
        BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
        Assert.assertEquals(d.kind(), BindDecision.Kind.EMPTY);
    }

    @Test
    public void bindSingle_usesHookNotTextXpath() {
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "xpath", "//button[normalize-space()='Save']", "button", "Save"),
                new DomCandidate("c2", "css", "button[data-axis-test-id='save-hook']", "button", "Save")
        );
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Save");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", cs, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("save-hook"),
                r.steps().get(0).locatorValue());
    }
}