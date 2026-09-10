package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class WorkbookJobMaterializerCallBeforeTest {

    private static ManualTestCase tc(String id, String callBefore) {
        return tc(id, id, callBefore);
    }

    private static ManualTestCase tc(String id, String title, String callBefore) {
        return new ManualTestCase(id, title, "", "1. Go", "1. Ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    @Test
    public void expandsCallBeforeForSelectedLeaf() {
        List<ManualTestCase> library = List.of(
                tc("TC_01", ""),
                tc("TC_06", "TC_01"));
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(library, List.of("TC_06"), List.of());
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_01", "TC_06"));
    }

    @Test
    public void expandsEachLibraryIdWhenNoSelection() {
        List<ManualTestCase> library = List.of(
                tc("TC_00", ""),
                tc("TC_01", "TC_00"),
                tc("TC_06", "TC_01"));
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(library, null, List.of());
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_00", "TC_00", "TC_01", "TC_00", "TC_01", "TC_06"));
    }

    @Test
    public void uploadWinsForCallBeforeResolution() {
        List<ManualTestCase> library = List.of(tc("TC_01", "Lib login", ""));
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(
                library, List.of("TC_06"), List.of(tc("TC_01", "Up login", ""), tc("TC_06", "TC_06", "TC_01")));
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(), List.of("TC_01", "TC_06"));
        Assert.assertEquals(out.get(0).title(), "Up login");
    }
}
