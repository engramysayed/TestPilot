package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;

public class ExcelTcReaderRejectTest {
    @Test
    public void message_forMissingColumn() {
        Assert.assertTrue(ExcelValidationMessages.missingColumn("Steps").contains("Steps"));
    }

    @Test
    public void rejectsMissingColumnsViaReader() {
        try {
            new ExcelTcReader().read(Path.of("src/test/resources/delivery/bad-missing-column.xlsx"));
            Assert.fail("expected reject");
        } catch (InvalidExcelTemplateException e) {
            Assert.assertEquals(e.getErrorCode(), "INVALID_EXCEL");
        }
    }
}
