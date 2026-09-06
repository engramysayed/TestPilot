package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorkbookUploadSupportTest {
    @Test
    public void convertsKeelCsvToXlsxReadableByExcelTcReader() throws Exception {
        String csv = """
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                TC_01,Sample,"1. Open home
                2. Confirm title","1. Home
                2. Title",No login required.,P1,,,,AUTOMATE
                """;
        Path dest = Files.createTempFile("keel-upload-", ".xlsx");
        try {
            WorkbookUploadSupport.materializeExcel("generated-tcs.csv",
                    csv.getBytes(StandardCharsets.UTF_8), dest);
            var cases = new ExcelTcReader().read(dest);
            Assert.assertEquals(cases.size(), 1);
            Assert.assertEquals(cases.get(0).tcId(), "TC_01");
            Assert.assertTrue(cases.get(0).steps().contains("Open home"));
        } finally {
            Files.deleteIfExists(dest);
        }
    }

    @Test
    public void detectsCsvByHeaderWhenFilenameMissing() {
        byte[] bytes = "TC_ID,Title,Steps\n".getBytes(StandardCharsets.UTF_8);
        Assert.assertTrue(WorkbookUploadSupport.looksLikeCsv(null, bytes));
        Assert.assertTrue(WorkbookUploadSupport.looksLikeCsv("export.csv", bytes));
        Assert.assertFalse(WorkbookUploadSupport.looksLikeCsv("sheet.xlsx", bytes));
    }
}
