package delivery.excel;

import delivery.excel.KeelPathCaseFilter.Surface;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class KeelPathSurfaceGuardTest {

    private static ManualTestCase tc(String id, String path) {
        return new ManualTestCase(id, id, "", "1. Click", "ok", "P1", "", "", "", path);
    }

    private static KeelPathCounts counts(String... paths) {
        List<ManualTestCase> cases = new java.util.ArrayList<>();
        for (int i = 0; i < paths.length; i++) {
            cases.add(tc("T" + (i + 1), paths[i]));
        }
        return KeelPathCounts.from(cases);
    }

    @Test
    public void hardBlock_automateWhenNoAutomateOrBlank() {
        KeelPathCounts c = counts("EXECUTE", "MANUAL", "VISION_ONLY");
        Optional<String> msg = KeelPathSurfaceGuard.hardBlock(Surface.AUTOMATE, c);
        Assert.assertTrue(msg.isPresent());
        Assert.assertTrue(msg.get().contains("AUTOMATE"));
    }

    @Test
    public void hardBlock_executeWhenOnlyManual() {
        KeelPathCounts c = counts("MANUAL", "MANUAL");
        Optional<String> msg = KeelPathSurfaceGuard.hardBlock(Surface.EXECUTE, c);
        Assert.assertTrue(msg.isPresent());
        Assert.assertTrue(msg.get().toLowerCase().contains("manual"));
    }

    @Test
    public void hardBlock_executeAllowsAutomateOnlyWorkbook() {
        KeelPathCounts c = counts("AUTOMATE", "MANUAL");
        Assert.assertTrue(KeelPathSurfaceGuard.hardBlock(Surface.EXECUTE, c).isEmpty());
    }

    @Test
    public void hardBlock_emptyWhenRunnablePresent() {
        KeelPathCounts automateOk = counts("AUTOMATE", "EXECUTE");
        Assert.assertTrue(KeelPathSurfaceGuard.hardBlock(Surface.AUTOMATE, automateOk).isEmpty());

        KeelPathCounts executeOk = counts("EXECUTE", "AUTOMATE");
        Assert.assertTrue(KeelPathSurfaceGuard.hardBlock(Surface.EXECUTE, executeOk).isEmpty());

        KeelPathCounts blankLegacy = counts("", "MANUAL");
        Assert.assertTrue(KeelPathSurfaceGuard.hardBlock(Surface.AUTOMATE, blankLegacy).isEmpty());
        Assert.assertTrue(KeelPathSurfaceGuard.hardBlock(Surface.EXECUTE, blankLegacy).isEmpty());
    }

    @Test
    public void softWarn_whenRunnableBelowHalfOfTcCount() {
        KeelPathCounts c = counts("AUTOMATE", "EXECUTE", "EXECUTE", "EXECUTE", "EXECUTE");
        Optional<String> msg = KeelPathSurfaceGuard.softWarn(Surface.AUTOMATE, c);
        Assert.assertTrue(msg.isPresent());
        Assert.assertTrue(msg.get().contains("1"));
        Assert.assertTrue(msg.get().contains("5"));
    }

    @Test
    public void softWarn_emptyWhenRunnableIsHalfOrMore() {
        KeelPathCounts half = counts("AUTOMATE", "AUTOMATE", "EXECUTE", "EXECUTE");
        Assert.assertTrue(KeelPathSurfaceGuard.softWarn(Surface.AUTOMATE, half).isEmpty());

        KeelPathCounts majority = counts("AUTOMATE", "AUTOMATE", "AUTOMATE", "EXECUTE");
        Assert.assertTrue(KeelPathSurfaceGuard.softWarn(Surface.AUTOMATE, majority).isEmpty());
    }

    @Test
    public void softWarn_emptyWhenHardBlockWouldApply() {
        KeelPathCounts c = counts("EXECUTE", "MANUAL");
        Assert.assertTrue(KeelPathSurfaceGuard.softWarn(Surface.AUTOMATE, c).isEmpty());
    }
}
