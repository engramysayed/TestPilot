package delivery.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CallBeforeWorkbookRoundTripTest {

    private static ManualTestCase sample(String callBefore) {
        return new ManualTestCase(
                "TC_02",
                "Depends on login",
                "Logged in",
                "1. Open dashboard",
                "1. Dashboard shown",
                "P1",
                "smoke",
                "",
                "",
                "AUTOMATE",
                callBefore);
    }

    @Test
    public void excel_roundTripPreservesCallBefore() throws Exception {
        ManualTestCase original = sample("TC_01");
        Path xlsx = Files.createTempFile("call-before-", ".xlsx");
        try {
            ManualTcExcelWriter.write(xlsx, List.of(original));
            List<ManualTestCase> read = new ExcelTcReader().read(xlsx);
            Assert.assertEquals(read.size(), 1);
            Assert.assertEquals(read.get(0).callBefore(), "TC_01");
        } finally {
            Files.deleteIfExists(xlsx);
        }
    }

    @Test
    public void excel_missingColumnDefaultsToEmpty() throws Exception {
        Path xlsx = Files.createTempFile("call-before-legacy-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(xlsx)) {
            var sheet = workbook.createSheet("Manual TCs");
            var header = sheet.createRow(0);
            String[] legacyHeaders = {
                    "TC_ID", "Title", "Steps", "ExpectedResult", "Preconditions",
                    "Priority", "Tags", "VisualAssertion", "TestData", "KeelPath"
            };
            for (int i = 0; i < legacyHeaders.length; i++) {
                header.createCell(i).setCellValue(legacyHeaders[i]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("TC_01");
            row.createCell(1).setCellValue("Legacy");
            row.createCell(2).setCellValue("1. Go");
            row.createCell(3).setCellValue("1. Ok");
            row.createCell(9).setCellValue("AUTOMATE");
            workbook.write(out);
        }
        try {
            List<ManualTestCase> read = new ExcelTcReader().read(xlsx);
            Assert.assertEquals(read.get(0).callBefore(), "");
        } finally {
            Files.deleteIfExists(xlsx);
        }
    }

    @Test
    public void csv_roundTripPreservesCallBefore() {
        ManualTestCase original = sample("TC_01,TC_00");
        String csv = GeneratedTcCsvParser.toCsv(List.of(original));
        List<ManualTestCase> read = GeneratedTcCsvParser.parse(csv);
        Assert.assertEquals(read.size(), 1);
        Assert.assertEquals(read.get(0).callBefore(), "TC_01,TC_00");
    }

    @Test
    public void csv_missingColumnDefaultsToEmpty() {
        String legacy = """
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                TC_01,Legacy,1. Go,1. Ok,,P1,,,,AUTOMATE
                """;
        List<ManualTestCase> read = GeneratedTcCsvParser.parse(legacy);
        Assert.assertEquals(read.get(0).callBefore(), "");
    }

    @Test
    public void json_parsePreservesCallBefore() {
        String json = """
                {
                  "testCases": [{
                    "tcId": "TC_02",
                    "title": "Depends on login",
                    "steps": "1. Open dashboard",
                    "expectedResult": "1. Dashboard shown",
                    "keelPath": "AUTOMATE",
                    "callBefore": "TC_01"
                  }]
                }
                """;
        GeneratedTcJsonParser.ParseResult result = GeneratedTcJsonParser.parse(json);
        Assert.assertEquals(result.cases().size(), 1);
        Assert.assertEquals(result.cases().get(0).callBefore(), "TC_01");
    }
}
