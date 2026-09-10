package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GeneratedWorkbookMergeUploadTest {

    private static ManualTestCase tc(String id, String title) {
        return new ManualTestCase(id, title, "", "1. Click", "Shown", "P1", "", "", "", "AUTOMATE");
    }

    @Test
    public void mergeKeepsUnmatchedAndReplacesSameId() {
        List<ManualTestCase> existing = List.of(tc("TC_01", "Old login"), tc("TC_02", "Keep me"));
        List<ManualTestCase> upload = List.of(tc("TC_01", "New login"), tc("TC_03", "Create user"));

        GeneratedWorkbookService.MergeResult merged = GeneratedWorkbookService.mergeByTcId(existing, upload);

        Assert.assertEquals(merged.cases().size(), 3);
        Assert.assertEquals(merged.cases().get(0).title(), "New login");
        Assert.assertEquals(merged.cases().get(1).title(), "Keep me");
        Assert.assertEquals(merged.cases().get(2).tcId(), "TC_03");
        Assert.assertEquals(merged.replacedCount(), 1);
        Assert.assertEquals(merged.addedCount(), 1);
    }

    @Test
    public void mergeIntoEmptyLibraryAddsAll() {
        GeneratedWorkbookService.MergeResult merged = GeneratedWorkbookService.mergeByTcId(
                List.of(), List.of(tc("TC_01", "A"), tc("TC_02", "B")));
        Assert.assertEquals(merged.cases().size(), 2);
        Assert.assertEquals(merged.addedCount(), 2);
        Assert.assertEquals(merged.replacedCount(), 0);
    }
}
