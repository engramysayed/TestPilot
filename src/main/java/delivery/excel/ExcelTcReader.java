package delivery.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import delivery.portal.model.KeelPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ExcelTcReader {
    private static final String[] REQUIRED = {"TCID", "TITLE", "STEPS", "EXPECTEDRESULT"};
    private static final DataFormatter FORMATTER = new DataFormatter();

    private final boolean allowDuplicateTcIds;

    public ExcelTcReader() {
        this(false);
    }

    /** Job workbooks may repeat a TC_ID when Call-before re-runs a case before another leaf. */
    public ExcelTcReader(boolean allowDuplicateTcIds) {
        this.allowDuplicateTcIds = allowDuplicateTcIds;
    }

    public List<ManualTestCase> read(Path excel) {
        if (excel == null || !Files.isRegularFile(excel)) {
            throw new InvalidExcelTemplateException(
                    ExcelValidationMessages.INVALID_EXCEL,
                    "Excel file not found: " + excel);
        }
        try (InputStream in = Files.newInputStream(excel);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new InvalidExcelTemplateException(
                        ExcelValidationMessages.INVALID_EXCEL,
                        ExcelValidationMessages.noDataRows());
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new InvalidExcelTemplateException(
                        ExcelValidationMessages.INVALID_EXCEL,
                        "Missing header row");
            }
            Map<String, Integer> columns = mapHeaders(header);
            for (String required : REQUIRED) {
                if (!columns.containsKey(required)) {
                    throw new InvalidExcelTemplateException(
                            ExcelValidationMessages.INVALID_EXCEL,
                            ExcelValidationMessages.missingColumn(displayName(required)));
                }
            }

            List<ManualTestCase> cases = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }
                String tcId = cell(row, columns.get("TCID"));
                if (tcId.isBlank()) {
                    throw new InvalidExcelTemplateException(
                            ExcelValidationMessages.INVALID_EXCEL,
                            ExcelValidationMessages.blankTcId(r + 1));
                }
                if (!allowDuplicateTcIds && !seenIds.add(tcId)) {
                    throw new InvalidExcelTemplateException(
                            ExcelValidationMessages.INVALID_EXCEL,
                            ExcelValidationMessages.duplicateTcId(tcId));
                }
                seenIds.add(tcId);
                String steps = ExcelStepText.normalizeMultiline(cellKeepNewlines(row, columns.get("STEPS")));
                String testData = ExcelStepText.normalizeMultiline(firstNonBlank(
                        cellKeepNewlines(row, columns.getOrDefault("TESTDATA", -1)),
                        cellKeepNewlines(row, columns.getOrDefault("STEPDATA", -1))));
                testData = alignTestDataToSteps(steps, testData);
                String visualAssertion = ExcelStepText.normalizeMultiline(
                        cellKeepNewlines(row, columns.getOrDefault("VISUALASSERTION", -1)));
                String keelPath = cell(row, columns.getOrDefault("KEELPATH", -1)).trim();
                if (keelPath.isBlank() && isKeelPathToken(testData)) {
                    keelPath = testData.trim();
                    testData = visualAssertion;
                    visualAssertion = "";
                }
                String callBefore = cell(row, columns.getOrDefault("CALLBEFORE", -1));
                // Blank KeelPath stays blank (legacy → eligible for both Automate and Execute).
                cases.add(new ManualTestCase(
                        tcId,
                        cell(row, columns.get("TITLE")),
                        cell(row, columns.getOrDefault("PRECONDITIONS", -1)),
                        steps,
                        ExcelStepText.normalizeMultiline(cell(row, columns.get("EXPECTEDRESULT"))),
                        cell(row, columns.getOrDefault("PRIORITY", -1)),
                        cell(row, columns.getOrDefault("TAGS", -1)),
                        visualAssertion,
                        testData,
                        keelPath,
                        callBefore
                ));
            }
            if (cases.isEmpty()) {
                throw new InvalidExcelTemplateException(
                        ExcelValidationMessages.INVALID_EXCEL,
                        ExcelValidationMessages.noDataRows());
            }
            return cases;
        } catch (InvalidExcelTemplateException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidExcelTemplateException(
                    ExcelValidationMessages.INVALID_EXCEL,
                    "Failed to read Excel: " + e.getMessage());
        } catch (RuntimeException e) {
            // POI may throw unchecked OpenXML errors for corrupt/non-xlsx uploads
            throw new InvalidExcelTemplateException(
                    ExcelValidationMessages.INVALID_EXCEL,
                    "Failed to read Excel: " + e.getMessage());
        }
    }

    private static Map<String, Integer> mapHeaders(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : header) {
            String raw = FORMATTER.formatCellValue(cell).trim();
            if (raw.isEmpty()) {
                continue;
            }
            map.put(normalizeHeader(raw), cell.getColumnIndex());
        }
        return map;
    }

    private static String normalizeHeader(String header) {
        return header.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String displayName(String normalized) {
        return switch (normalized) {
            case "TCID" -> "TC_ID";
            case "EXPECTEDRESULT" -> "ExpectedResult";
            case "TITLE" -> "Title";
            case "STEPS" -> "Steps";
            default -> normalized;
        };
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    /**
     * Pad / trim TestData lines so line N always pairs with Steps line N (blank lines count).
     * Trailing blanks lost to stripTrailing are restored as empty strings.
     */
    static String alignTestDataToSteps(String steps, String testData) {
        String[] stepLines = (steps == null ? "" : steps).split("\n", -1);
        String[] dataLines = (testData == null ? "" : testData).split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < stepLines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(i < dataLines.length ? dataLines[i] : "");
        }
        return out.toString();
    }

    private static String cellKeepNewlines(Row row, int index) {
        if (index < 0) {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        String raw = FORMATTER.formatCellValue(cell);
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private static String cell(Row row, int index) {
        if (index < 0) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private static boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (!FORMATTER.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isKeelPathToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            KeelPath.parse(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
