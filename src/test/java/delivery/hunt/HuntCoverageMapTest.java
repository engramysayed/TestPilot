package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class HuntCoverageMapTest {
    @Test
    public void tracksVisitAndStuckStreak() throws Exception {
        Path root = Files.createTempDirectory("hunt-cov");
        HuntCoverageMap cov = new HuntCoverageMap(root);
        cov.noteVisit("https://ex/a", "A", "Heading A");
        cov.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
        cov.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
        Assert.assertTrue(cov.shouldStopStuck());
        Assert.assertTrue(cov.forPrompt().contains("https://ex/a"));
        Assert.assertTrue(Files.isRegularFile(root.resolve("coverage-map.md")));
    }

    @Test
    public void failStreakResetsOnOk() throws Exception {
        Path root = Files.createTempDirectory("hunt-cov-ok");
        HuntCoverageMap cov = new HuntCoverageMap(root);
        cov.recordActions(List.of(Map.of(
                "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
        cov.recordActions(List.of(Map.of(
                "type", "click", "status", "ok", "locator", "#go")));
        Assert.assertEquals(cov.failStreak("click", "#go"), 0);
        Assert.assertFalse(cov.shouldStopStuck());
    }

    @Test
    public void markStrategyDoneAppendsToFile() throws Exception {
        Path root = Files.createTempDirectory("hunt-cov-strat");
        HuntCoverageMap cov = new HuntCoverageMap(root);
        cov.markStrategyDone("happy");
        String md = Files.readString(root.resolve("coverage-map.md"));
        Assert.assertTrue(md.contains("happy"));
        Assert.assertTrue(cov.forPrompt().contains("happy"));
    }

    @Test
    public void locatorKeyUsesStrategyAndValue() throws Exception {
        Path root = Files.createTempDirectory("hunt-cov-loc");
        HuntCoverageMap cov = new HuntCoverageMap(root);
        cov.recordActions(List.of(Map.of(
                "type", "type", "status", "fail",
                "locatorStrategy", "css", "locatorValue", "#user", "reason", "missing")));
        cov.recordActions(List.of(Map.of(
                "type", "type", "status", "fail",
                "locatorStrategy", "css", "locatorValue", "#user", "reason", "missing")));
        Assert.assertEquals(cov.failStreak("type", "css:#user"), 2);
        Assert.assertTrue(cov.shouldStopStuck());
    }
}
