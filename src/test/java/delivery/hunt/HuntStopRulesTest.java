package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HuntStopRulesTest {

    @Test
    public void recoveryBlocksLocatorInsteadOfStoppingWhenStrategiesDisabled() throws Exception {
        Path root = Files.createTempDirectory("hunt-stop-stuck");
        HuntCoverageMap coverage = new HuntCoverageMap(root);
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));

        Assert.assertTrue(coverage.shouldStopStuck());
        HuntStopRules.Recovery recovery = HuntStopRules.recoverFromStuck(coverage, false, null);

        Assert.assertTrue(recovery.triggered());
        Assert.assertEquals(recovery.blockedLocators(), List.of("click `#go`"));
        Assert.assertEquals(recovery.advancedFrom(), "");
        Assert.assertFalse(coverage.shouldStopStuck(), "streak must be cleared so the hunt continues");
        Assert.assertTrue(coverage.forPrompt().contains("## Do not retry (blocked)"));
        Assert.assertTrue(coverage.forPrompt().contains("click `#go`"));
    }

    @Test
    public void recoveryAdvancesStrategyWhenEnabledAndNotLast() throws Exception {
        Path root = Files.createTempDirectory("hunt-stop-advance");
        HuntCoverageMap coverage = new HuntCoverageMap(root);
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));

        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        Assert.assertEquals(seq.current().mode(), "happy");

        HuntStopRules.Recovery recovery = HuntStopRules.recoverFromStuck(coverage, true, seq);
        Assert.assertEquals(recovery.advancedFrom(), "happy");
        Assert.assertEquals(recovery.advancedTo(), "empty");
        Assert.assertFalse(coverage.shouldStopStuck());
        Assert.assertEquals(seq.current().mode(), "empty");
    }

    @Test
    public void recoveryOnLastModeStillContinuesWithBlockedLocator() throws Exception {
        Path root = Files.createTempDirectory("hunt-stop-invent");
        HuntCoverageMap coverage = new HuntCoverageMap(root);
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));

        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        for (int i = 0; i < 5; i++) {
            seq.advance();
        }
        Assert.assertEquals(seq.current().mode(), "invent");
        Assert.assertTrue(seq.isLast());

        HuntStopRules.Recovery recovery = HuntStopRules.recoverFromStuck(coverage, true, seq);
        Assert.assertEquals(recovery.blockedLocators(), List.of("click `#go`"));
        Assert.assertEquals(recovery.advancedFrom(), "");
        Assert.assertFalse(coverage.shouldStopStuck());
    }

    @Test
    public void recoveryNotTriggeredWhenNotStuck() throws Exception {
        Path root = Files.createTempDirectory("hunt-stop-ok");
        HuntCoverageMap coverage = new HuntCoverageMap(root);
        coverage.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));

        Assert.assertFalse(coverage.shouldStopStuck());
        Assert.assertFalse(HuntStopRules.recoverFromStuck(coverage, false, null).triggered());
    }

    @Test
    public void completeStopWhenPlaybookExhaustedInventBudgetMetAndBugsPresent() {
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        while (!seq.isLast()) {
            seq.advance();
        }
        seq.completeCurrent();
        Assert.assertTrue(seq.finishedAll());

        Assert.assertEquals(
                HuntStopRules.completeStop(true, seq, 5, 5, 2),
                Optional.of("COMPLETE"));
        Assert.assertEquals(
                HuntStopRules.resolveFinishStopReason(true, seq, 5, 5, 2),
                "COMPLETE");
    }

    @Test
    public void completeStopEmptyWhenNoBugsOrBudgetNotMet() {
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        while (!seq.isLast()) {
            seq.advance();
        }
        seq.completeCurrent();

        Assert.assertEquals(
                HuntStopRules.completeStop(true, seq, 5, 5, 0),
                Optional.empty());
        Assert.assertEquals(
                HuntStopRules.completeStop(true, seq, 3, 5, 1),
                Optional.empty());
        Assert.assertEquals(
                HuntStopRules.resolveFinishStopReason(true, seq, 3, 5, 1),
                "FINISH");
    }

    @Test
    public void completeStopEmptyWhenStrategiesDisabled() {
        HuntStrategySequencer seq = new HuntStrategySequencer(false);
        Assert.assertEquals(
                HuntStopRules.completeStop(false, seq, 5, 5, 3),
                Optional.empty());
    }

    @Test
    public void summaryIncludesOracleCountAndCompleteStopReason() {
        HuntRequest req = new HuntRequest();
        req.setJobId("hunt_complete");
        req.setProjectId("prj_c");
        req.setScenarioCap(5);
        req.normalize();

        String summary = HuntPackWriter.buildSummary(
                req, "COMPLETE", 4, 3, 5, "enabled", 0,
                List.of("happy", "empty", "boundary", "abuse", "session", "invent"), 2);

        Assert.assertTrue(summary.contains("Stop reason: COMPLETE"));
        Assert.assertTrue(summary.contains("Bugs: 3 (oracle: 2)"));
        Assert.assertTrue(summary.contains("Strategies completed: happy, empty, boundary, abuse, session, invent"));
        Assert.assertTrue(summary.contains("Coverage URLs visited:"));
    }

    @Test
    public void repeatedFailuresBlockLocatorAcrossCyclesWithoutStopping() throws Exception {
        Path root = Files.createTempDirectory("hunt-stop-summary");
        HuntCoverageMap coverage = new HuntCoverageMap(root);
        for (int cycle = 1; cycle <= 3; cycle++) {
            coverage.recordActions(List.of(Map.of(
                    "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
            coverage.recordActions(List.of(Map.of(
                    "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
            HuntStopRules.recoverFromStuck(coverage, false, null);
            Assert.assertFalse(coverage.shouldStopStuck(), "cycle " + cycle + " must not end the hunt");
        }
        Assert.assertEquals(coverage.blockedLocators(), List.of("click `#go`"));
    }
}
