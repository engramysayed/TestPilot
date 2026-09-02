package delivery.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExcelTcReaderTest {
    private final ExcelTcReader reader = new ExcelTcReader();

    @Test
    public void readsRows_whenHeadersMatchTemplate() {
        var path = Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx");
        var rows = reader.read(path);
        Assert.assertFalse(rows.isEmpty());
        Assert.assertEquals(rows.get(0).tcId(), "TC_001");
    }

    @Test
    public void rejects_whenRequiredColumnMissing() {
        var path = Path.of("src/test/resources/delivery/bad-missing-column.xlsx");
        try {
            reader.read(path);
            Assert.fail("expected InvalidExcelTemplateException");
        } catch (InvalidExcelTemplateException e) {
            Assert.assertEquals(e.getErrorCode(), ExcelValidationMessages.INVALID_EXCEL);
            Assert.assertTrue(e.getMessage().contains("Missing required column"));
        }
    }

    @Test
    public void missingVisualAssertionColumn_defaultsEmpty() throws Exception {
        Path path = writeSheet(new String[] {
                "TC_ID", "Title", "Steps", "ExpectedResult"
        }, new String[] {"TC_VA_01", "Login", "click login", "Welcome"});
        try {
            List<ManualTestCase> rows = reader.read(path);
            Assert.assertEquals(rows.size(), 1);
            Assert.assertEquals(rows.get(0).visualAssertion(), "");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void visualAssertionHeader_populatesField() throws Exception {
        Path path = writeSheet(new String[] {
                "TC_ID", "Title", "Steps", "ExpectedResult", "Visual Assertion"
        }, new String[] {
                "TC_VA_02", "Login", "click login", "Welcome",
                "Welcome heading is visible"
        });
        try {
            List<ManualTestCase> rows = reader.read(path);
            Assert.assertEquals(rows.get(0).visualAssertion(), "Welcome heading is visible");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testDataHeader_populatesField() throws Exception {
        Path path = writeSheet(new String[] {
                "TC_ID", "Title", "Steps", "ExpectedResult", "TestData"
        }, new String[] {
                "TC_TD_01", "Reg", "Enter in the First name field", "ok", "Alice"
        });
        try {
            List<ManualTestCase> rows = reader.read(path);
            Assert.assertEquals(rows.get(0).testData(), "Alice");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testDataKeepsLeadingBlankLinesForStepAlignment() throws Exception {
        Path path = writeSheet(new String[] {
                "TC_ID", "Title", "Steps", "ExpectedResult", "TestData"
        }, new String[] {
                "TC_TD_02",
                "Reg",
                "1. Open the form at /reg/\n2. Enter in the First name field",
                "ok",
                "\nNora"
        });
        try {
            List<ManualTestCase> rows = reader.read(path);
            Assert.assertTrue(rows.get(0).testData().startsWith("\n"),
                    "leading blank TestData line must survive Excel read, got: "
                            + rows.get(0).testData().replace("\n", "\\n"));
            var enter = delivery.authoring.StepIntentBinder.bodyIntents(rows.get(0), false).stream()
                    .filter(i -> i.kind() == delivery.authoring.StepIntentBinder.IntentKind.TYPE_FIELD)
                    .findFirst()
                    .orElseThrow();
            Assert.assertEquals(enter.testData(), "Nora");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testDataPadsTrailingBlanksToMatchStepLineCount() throws Exception {
        // Steps has 4 lines; TestData loses trailing blanks after stripTrailing — pad restores alignment.
        Path path = writeSheet(new String[] {
                "TC_ID", "Title", "Steps", "ExpectedResult", "TestData"
        }, new String[] {
                "TC_TD_03",
                "Checkout",
                "1. Open\n2. Enter in the First Name field\n3. Enter in the Last Name field\n4. Click Continue",
                "ok",
                "\nJohn\nDoe"
        });
        try {
            List<ManualTestCase> rows = reader.read(path);
            String[] steps = rows.get(0).steps().split("\n", -1);
            String[] data = rows.get(0).testData().split("\n", -1);
            Assert.assertEquals(data.length, steps.length,
                    "TestData lines must match Steps lines");
            Assert.assertEquals(data[1], "John");
            Assert.assertEquals(data[2], "Doe");
            Assert.assertEquals(data[3], "");
            var intents = delivery.authoring.StepIntentBinder.parseIntents(rows.get(0));
            var first = intents.stream()
                    .filter(i -> i.text().toLowerCase().contains("first name"))
                    .findFirst()
                    .orElseThrow();
            Assert.assertEquals(first.testData(), "John");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static Path writeSheet(String[] headers, String[] values) throws Exception {
        Path path = Files.createTempFile("excel-va-", ".xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }
            try (var out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
        return path;
    }
}
