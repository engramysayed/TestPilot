package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class BindDecisionHealPolicyTest {

    @Test
    public void shortlistClearIsSingleWinner() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "css", "span[data-axis-test-id='side-nav-support-submenu-button']",
                        "span", "Support"),
                new DomCandidate("c2", "id", "home", "a", "Home")
        );
        List<DomCandidate> sl = CandidateScorer.shortlist(intent, cs);
        Assert.assertEquals(sl.size(), 1);
        Assert.assertTrue(sl.get(0).value().contains("support"));
    }

    @Test
    public void shortlistTieCapsAtFive() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Delete");
        List<DomCandidate> cs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            cs.add(new DomCandidate("c" + i, "id", "del-" + i, "button", "Delete"));
        }
        List<DomCandidate> sl = CandidateScorer.shortlist(intent, cs);
        Assert.assertTrue(sl.size() <= CandidateScorer.SHORTLIST_MAX, String.valueOf(sl.size()));
        Assert.assertTrue(sl.size() >= 2);
    }

    @Test
    public void authoringShortlistRespectsDecisionCap() {
        AuthoringService svc = new AuthoringService(null, new LocatorValidator());
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Delete");
        List<DomCandidate> cs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            cs.add(new DomCandidate("c" + i, "id", "del-" + i, "button", "Delete"));
        }
        List<DomCandidate> sl = svc.shortlistForIntent(intent, cs, 12);
        Assert.assertTrue(sl.size() <= CandidateScorer.SHORTLIST_MAX, String.valueOf(sl.size()));
        Assert.assertTrue(sl.size() >= 2);
    }
}