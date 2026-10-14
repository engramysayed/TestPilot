package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

/** B15 unit checks: field diff and problem field mapping. */
public class WorkbookUploadPreviewServiceTest {
    @Test
    public void changedFieldsIgnoresWhitespaceAndLineEndings() {
        ManualTestCase a = new ManualTestCase("TC_1", "T", "", "1. a\r\n2. b", "ok", "P1", "", "", "", "AUTOMATE");
        ManualTestCase b = new ManualTestCase("TC_1", "T ", null, "1. a\n2. b", "ok", "P2", "", "", "d", "AUTOMATE");
        Assert.assertEquals(WorkbookUploadPreviewService.changedFields(a, b), java.util.List.of("Priority", "TestData"));
        Assert.assertEquals(WorkbookUploadPreviewService.guessField("Expected result is empty"), "ExpectedResult");
        Assert.assertNull(WorkbookUploadPreviewService.guessField("something else"));
    }
}
