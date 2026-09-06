package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class WorkbookJobMaterializerTest {

    private static ManualTestCase tc(String id, String title) {
        return new ManualTestCase(id, title, "", "1. Open", "1. OK", "P1", "", "", "", "AUTOMATE");
    }

    @Test
    public void allLibraryWhenNoSelectionNoUpload() {
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(
                List.of(tc("TC_01", "A"), tc("TC_02", "B")), null, List.of());
        Assert.assertEquals(out.size(), 2);
    }

    @Test
    public void filtersLibraryBySelection() {
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(
                List.of(tc("TC_01", "A"), tc("TC_02", "B")), List.of("TC_02"), List.of());
        Assert.assertEquals(out.size(), 1);
        Assert.assertEquals(out.get(0).tcId(), "TC_02");
    }

    @Test
    public void uploadWinsOnDuplicateTcId() {
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(
                List.of(tc("TC_01", "Library")),
                null,
                List.of(tc("TC_01", "Upload")));
        Assert.assertEquals(out.size(), 1);
        Assert.assertEquals(out.get(0).title(), "Upload");
    }

    @Test
    public void mixKeepsLibraryPlusUpload() {
        List<ManualTestCase> out = WorkbookJobMaterializer.merge(
                List.of(tc("TC_01", "Lib1"), tc("TC_02", "Lib2")),
                List.of("TC_01"),
                List.of(tc("TC_03", "Up3")));
        Assert.assertEquals(out.size(), 2);
        Assert.assertEquals(out.get(0).tcId(), "TC_01");
        Assert.assertEquals(out.get(1).tcId(), "TC_03");
    }
}
