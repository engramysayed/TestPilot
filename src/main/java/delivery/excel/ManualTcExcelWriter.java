package delivery.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes Keel manual TC rows to the first sheet of an .xlsx workbook. */
public final class ManualTcExcelWriter {
    private static final String[] HEADERS = {
            "TC_ID", "Title", "Steps", "ExpectedResult", "Preconditions",
            "Priority", "Tags", "VisualAssertion", "TestData", "KeelPath"
    };

    private ManualTcExcelWriter() {
    }

    public static void write(Path excelPath, List<ManualTestCase> cases) throws IOException {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("No test cases to write");
        }
        Files.createDirectories(excelPath.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(excelPath)) {
            Sheet sheet = workbook.createSheet("Manual TCs");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIndex = 1;
            for (ManualTestCase tc : cases) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(tc.tcId());
                row.createCell(1).setCellValue(tc.title());
                row.createCell(2).setCellValue(tc.steps());
                row.createCell(3).setCellValue(tc.expectedResult());
                row.createCell(4).setCellValue(tc.preconditions());
                row.createCell(5).setCellValue(tc.priority());
                row.createCell(6).setCellValue(tc.tags());
                row.createCell(7).setCellValue(tc.visualAssertion());
                row.createCell(8).setCellValue(tc.testData());
                row.createCell(9).setCellValue(tc.keelPath());
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
        }
    }
}
