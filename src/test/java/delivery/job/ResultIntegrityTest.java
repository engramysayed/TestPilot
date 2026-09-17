package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class ResultIntegrityTest {

    @Test
    public void simulatedPassesDoNotInflateProvenPassRate() {
        ResultIntegrity.Summary mixed = ResultIntegrity.summarize(
                ResultIntegrity.JobSlice.proven(1, 1),
                ResultIntegrity.JobSlice.simulated(10, 0));
        Assert.assertEquals(mixed.provenPassed(), 1);
        Assert.assertEquals(mixed.blocked(), 1);
        Assert.assertEquals(mixed.simulated(), 10);
        Assert.assertEquals(mixed.passRate(), 50);
        Assert.assertTrue(mixed.denominatorNote().toLowerCase().contains("simulated"));
        Assert.assertFalse(mixed.denominatorNote().toLowerCase().contains("100"));
    }

    @Test
    public void uncheckedCasesStayOutOfTheNumerator() {
        ResultIntegrity.Summary s = ResultIntegrity.summarize(
                ResultIntegrity.JobSlice.unchecked(4));
        Assert.assertEquals(s.provenPassed(), 0);
        Assert.assertEquals(s.unchecked(), 4);
        Assert.assertEquals(s.passRate(), 0);
        Assert.assertTrue(s.denominatorNote().toLowerCase().contains("unchecked"));
    }

    @Test
    public void browserProofIsDistinctFromFrameworkReplay() {
        Assert.assertEquals(ResultIntegrity.proofSource("FRESH", false), "BROWSER_PROOF");
        Assert.assertEquals(ResultIntegrity.proofSource("SIMULATED", true), "SIMULATED");
        Assert.assertEquals(ResultIntegrity.proofSource("FRESH", true), "FRAMEWORK_REPLAY");
    }

    @Test
    public void describeMapExposesCountsForUi() {
        Map<String, Object> body = ResultIntegrity.summarize(
                ResultIntegrity.JobSlice.proven(2, 1)).asMap();
        Assert.assertEquals(body.get("provenPassed"), 2);
        Assert.assertEquals(body.get("blocked"), 1);
        Assert.assertEquals(body.get("passRate"), 67);
        Assert.assertEquals(body.get("hasProvenSample"), true);
        Assert.assertTrue(String.valueOf(body.get("denominatorNote")).contains("3"));
    }

    @Test
    public void emptyJudgedDoesNotPresentZeroOverZeroAsAMeasuredRate() {
        ResultIntegrity.Summary empty = ResultIntegrity.summarize();
        Assert.assertEquals(empty.passRate(), 0);
        Assert.assertFalse(empty.hasProvenSample());
        Assert.assertEquals(empty.denominatorNote(), "No proven cases yet");
        ResultIntegrity.Summary simulatedOnly = ResultIntegrity.summarize(
                ResultIntegrity.JobSlice.simulated(4, 0));
        Assert.assertFalse(simulatedOnly.hasProvenSample());
        Assert.assertEquals(simulatedOnly.denominatorNote(), "No proven cases yet");
        Assert.assertFalse(simulatedOnly.denominatorNote().contains("0/0"));
    }
}
