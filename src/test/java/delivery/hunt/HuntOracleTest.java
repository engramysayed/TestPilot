package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HuntOracleTest {

    @Test
    public void networkFailureDraftsBugWithReproWhenPlannerSilent() throws Exception {
        Path root = Files.createTempDirectory("hunt-oracle-net");
        HuntStepsJournal journal = new HuntStepsJournal(root);
        journal.appendCycleHeader(1, "continue", "probe checkout");
        journal.appendActions(1, List.of(
                Map.of("type", "click", "status", "ok", "locator", "#submit")
        ));
        String repro = journal.reproSlice();

        List<Map<String, Object>> netFails = List.of(
                Map.of("type", "http_error", "status", 500, "url", "https://example.com/api/checkout")
        );

        List<Map<String, Object>> bugs = HuntOracle.collect(
                1, netFails, List.of(), List.of(), repro, List.of());

        Assert.assertEquals(bugs.size(), 1);
        Map<String, Object> bug = bugs.get(0);
        Assert.assertTrue(String.valueOf(bug.get("title")).contains("500"));
        Assert.assertEquals(bug.get("severity"), "major");
        Assert.assertFalse(String.valueOf(bug.get("repro")).isBlank());
        Assert.assertTrue(String.valueOf(bug.get("repro")).contains("#submit"));
        Assert.assertFalse(String.valueOf(bug.get("expected")).isBlank());
        Assert.assertFalse(String.valueOf(bug.get("actual")).isBlank());
    }

    @Test
    public void skipsNetworkBugWhenPlannerAlreadyReported() {
        List<Map<String, Object>> plannerBugs = List.of(
                Map.of("title", "Server error", "severity", "major", "actual", "500")
        );
        List<Map<String, Object>> netFails = List.of(
                Map.of("type", "http_error", "status", 500, "url", "https://example.com/api")
        );

        List<Map<String, Object>> bugs = HuntOracle.collect(
                1, netFails, List.of(), List.of(), "repro", plannerBugs);

        Assert.assertTrue(bugs.isEmpty());
    }

    @Test
    public void failedAssertDraftedWhenNotInPlannerBugs() {
        List<Map<String, Object>> actionLog = List.of(
                Map.of("type", "assert_text", "status", "fail", "reason", "text not found",
                        "expected", "Welcome")
        );

        List<Map<String, Object>> bugs = HuntOracle.collect(
                2, List.of(), List.of(), actionLog, "steps so far", List.of());

        Assert.assertEquals(bugs.size(), 1);
        Assert.assertTrue(String.valueOf(bugs.get(0).get("title")).contains("assert_text"));
        Assert.assertEquals(bugs.get(0).get("repro"), "steps so far");
    }

    @Test
    public void alertsAfterFailActionDraftMajorBug() {
        List<Map<String, Object>> actionLog = List.of(
                Map.of("type", "click", "status", "fail", "reason", "not clickable", "locator", "#save")
        );
        List<String> alerts = List.of("Error: payment declined");

        List<Map<String, Object>> bugs = HuntOracle.collect(
                3, List.of(), alerts, actionLog, "journal tail", List.of());

        Assert.assertEquals(bugs.size(), 1);
        Assert.assertEquals(bugs.get(0).get("severity"), "major");
        Assert.assertTrue(String.valueOf(bugs.get(0).get("title")).toLowerCase().contains("alert"));
        Assert.assertEquals(bugs.get(0).get("repro"), "journal tail");
    }

    @Test
    public void loadingFailedDraftsBugWhenPlannerSilent() {
        List<Map<String, Object>> netFails = List.of(
                Map.of("type", "loading_failed", "errorText", "net::ERR_CONNECTION_REFUSED",
                        "url", "https://example.com/api")
        );
        List<Map<String, Object>> bugs = HuntOracle.collect(
                1, netFails, List.of(), List.of(), "repro", List.of());
        Assert.assertEquals(bugs.size(), 1);
        Assert.assertTrue(String.valueOf(bugs.get(0).get("title")).toLowerCase().contains("network"));
        Assert.assertEquals(bugs.get(0).get("severity"), "major");
    }

    @Test
    public void blankMainAfterNavigateDraftsBug() {
        HuntOracle.PageSignals page = new HuntOracle.PageSignals(
                "https://app.example/empty", 5, List.of("https://app.example/home"),
                "happy", true);
        List<Map<String, Object>> actionLog = List.of(
                Map.of("type", "navigate", "status", "ok", "url", "https://app.example/empty")
        );
        List<Map<String, Object>> bugs = HuntOracle.collect(
                1, List.of(), List.of(), actionLog, "repro", List.of(), page);
        Assert.assertEquals(bugs.size(), 1);
        Assert.assertTrue(String.valueOf(bugs.get(0).get("title")).toLowerCase().contains("blank"));
    }

    @Test
    public void unexpectedLoginRedirectDraftsBug() {
        HuntOracle.PageSignals page = new HuntOracle.PageSignals(
                "https://app.example/login", 200,
                List.of("https://app.example/dashboard"), "empty", true);
        List<Map<String, Object>> bugs = HuntOracle.collect(
                1, List.of(), List.of(), List.of(), "repro", List.of(), page);
        Assert.assertEquals(bugs.size(), 1);
        Assert.assertTrue(String.valueOf(bugs.get(0).get("title")).toLowerCase().contains("redirect"));
    }

    @Test
    public void sessionStrategySkipsUnexpectedLoginUrl() {
        HuntOracle.PageSignals page = new HuntOracle.PageSignals(
                "https://app.example/login", 200,
                List.of("https://app.example/dashboard"), "session", true);
        List<Map<String, Object>> bugs = HuntOracle.collect(
                1, List.of(), List.of(), List.of(), "repro", List.of(), page);
        Assert.assertTrue(bugs.isEmpty());
    }

    @Test
    public void finishedAllWhenEveryModeCompleted() {
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        Assert.assertFalse(seq.finishedAll());
        while (!seq.isLast()) {
            seq.advance();
        }
        Assert.assertFalse(seq.finishedAll());
        seq.completeCurrent();
        Assert.assertTrue(seq.finishedAll());
        Assert.assertEquals(seq.completed().size(), 6);
    }
}
