package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class KeelPathCaseFilterTest {

    private static ManualTestCase tc(String id, String path) {
        return new ManualTestCase(id, id, "", "1. Click", "ok", "P1", "", "", "", path);
    }

    @Test
    public void automate_keepsAutomateExecuteVisionAndBlank_skipsManualOnly() {
        List<ManualTestCase> filtered = KeelPathCaseFilter.forSurface(List.of(
                tc("A", "AUTOMATE"),
                tc("E", "EXECUTE"),
                tc("V", "VISION_ONLY"),
                tc("M", "MANUAL"),
                tc("B", "")
        ), KeelPathCaseFilter.Surface.AUTOMATE);

        Assert.assertEquals(filtered.stream().map(ManualTestCase::tcId).toList(), List.of("A", "E", "V", "B"));
    }

    @Test
    public void execute_keepsAutomateExecuteVisionAndBlank_skipsManualOnly() {
        List<ManualTestCase> filtered = KeelPathCaseFilter.forSurface(List.of(
                tc("A", "AUTOMATE"),
                tc("E", "EXECUTE"),
                tc("V", "VISION_ONLY"),
                tc("M", "MANUAL"),
                tc("B", "")
        ), KeelPathCaseFilter.Surface.EXECUTE);

        Assert.assertEquals(filtered.stream().map(ManualTestCase::tcId).toList(), List.of("A", "E", "V", "B"));
    }

    @Test
    public void blankKeelPath_eligibleOnBothSurfaces() {
        ManualTestCase blank = new ManualTestCase("TC_01", "t", "", "1. x", "1. y", "", "", "", "", "");
        Assert.assertEquals(KeelPathCaseFilter.forSurface(List.of(blank), KeelPathCaseFilter.Surface.AUTOMATE).size(), 1);
        Assert.assertEquals(KeelPathCaseFilter.forSurface(List.of(blank), KeelPathCaseFilter.Surface.EXECUTE).size(), 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void require_throwsWhenNoEligibleRows() {
        KeelPathCaseFilter.requireForSurface(
                List.of(tc("M", "MANUAL")),
                KeelPathCaseFilter.Surface.AUTOMATE,
                "none"
        );
    }
}
