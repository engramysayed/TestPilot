package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

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
}