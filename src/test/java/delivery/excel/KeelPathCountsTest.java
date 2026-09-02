package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class KeelPathCountsTest {

    private static ManualTestCase tc(String id, String path) {
        return new ManualTestCase(id, id, "", "1. Click", "ok", "P1", "", "", "", path);
    }

    @Test
    public void blankIsBlankNotExecute() {
        List<ManualTestCase> cases = List.of(
                tc("T1", "AUTOMATE"),
                tc("T2", ""),
                tc("T3", "EXECUTE"),
                tc("T4", "MANUAL"));
        KeelPathCounts c = KeelPathCounts.from(cases);
        Assert.assertEquals(c.get("AUTOMATE"), 1);
        Assert.assertEquals(c.get("BLANK"), 1);
        Assert.assertEquals(c.get("EXECUTE"), 1);
        Assert.assertEquals(c.get("MANUAL"), 1);
        Assert.assertEquals(c.automateRunnable(), 2);
        Assert.assertEquals(c.executeRunnable(), 3); // AUTOMATE + EXECUTE + BLANK (not MANUAL)
    }

    @Test
    public void aliasesNormalizeToCanonicalKeys() {
        List<ManualTestCase> cases = List.of(
                tc("T1", "AUTO"),
                tc("T2", "VISION"),
                tc("T3", "RUN"));
        KeelPathCounts c = KeelPathCounts.from(cases);
        Assert.assertEquals(c.get("AUTOMATE"), 1);
        Assert.assertEquals(c.get("VISION_ONLY"), 1);
        Assert.assertEquals(c.get("EXECUTE"), 1);
        Assert.assertEquals(c.automateRunnable(), 1);
        Assert.assertEquals(c.executeRunnable(), 3);
    }
}
