package delivery.excel;

import delivery.portal.model.KeelPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses Keel TC CSV from LLM output (RFC4180-style quoted fields). */
public final class GeneratedTcCsvParser {
    private static final String EXPECTED_HEADER =
            "TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath,CallBefore";

    private GeneratedTcCsvParser() {
    }

    public static List<ManualTestCase> parse(String rawCsv) {
        String csv = extractCsvBlock(rawCsv);
        List<String[]> rows = parseRows(csv);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV has no rows");
        }
        String[] header = rows.get(0);
        Map<String, Integer> cols = mapHeader(header);
        validateRequired(cols);
        List<ManualTestCase> out = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            String tcId = cell(row, cols.get("TCID"));
            if (tcId.isBlank()) {
                continue;
            }
            String steps = ExcelStepText.normalizeMultiline(cell(row, cols.get("STEPS")));
            String expectedResult = ExcelStepText.normalizeMultiline(cell(row, cols.get("EXPECTEDRESULT")));
            String visualAssertion = ExcelStepText.normalizeMultiline(
                    cell(row, cols.getOrDefault("VISUALASSERTION", -1)));
            String testData = ExcelStepText.normalizeMultiline(cell(row, cols.getOrDefault("TESTDATA", -1)));
            String keelRaw = cell(row, cols.get("KEELPATH")).trim();
            String[] aligned = realignKeelColumns(visualAssertion, testData, keelRaw);
            visualAssertion = aligned[0];
            testData = aligned[1];
            keelRaw = aligned[2];
            testData = ExcelTcReader.alignTestDataToSteps(steps, testData);
            if (!keelRaw.isBlank()) {
                KeelPath.parse(keelRaw);
            }
            String keelStored = keelRaw.isBlank() ? "" : keelRaw.trim().toUpperCase(Locale.ROOT);
            out.add(new ManualTestCase(
                    tcId,
                    cell(row, cols.get("TITLE")),
                    cell(row, cols.getOrDefault("PRECONDITIONS", -1)),
                    steps,
                    expectedResult,
                    cell(row, cols.getOrDefault("PRIORITY", -1)),
                    cell(row, cols.getOrDefault("TAGS", -1)),
                    visualAssertion,
                    testData,
                    keelStored,
                    cell(row, cols.getOrDefault("CALLBEFORE", -1))
            ));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("CSV has no data rows");
        }
        out = GeneratedTcCsvRepair.repair(out);
        for (ManualTestCase tc : out) {
            if (!GeneratedTcCsvRepair.isWellFormedTcId(tc.tcId())) {
                throw new IllegalArgumentException(
                        "Invalid TC_ID '" + tc.tcId() + "' — must be TC_01, TC_02, … with steps in the Steps column");
            }
        }
        return out;
    }

    public static String toCsv(List<ManualTestCase> cases) {
        StringBuilder sb = new StringBuilder(EXPECTED_HEADER).append('\n');
        for (ManualTestCase tc : cases) {
            sb.append(csvCell(tc.tcId())).append(',');
            sb.append(csvCell(tc.title())).append(',');
            sb.append(csvCell(tc.steps())).append(',');
            sb.append(csvCell(tc.expectedResult())).append(',');
            sb.append(csvCell(tc.preconditions())).append(',');
            sb.append(csvCell(tc.priority())).append(',');
            sb.append(csvCell(tc.tags())).append(',');
            sb.append(csvCell(tc.visualAssertion())).append(',');
            sb.append(csvCell(tc.testData())).append(',');
            sb.append(csvCell(tc.keelPath())).append(',');
            sb.append(csvCell(tc.callBefore())).append('\n');
        }
        return sb.toString();
    }

    public static String extractCsvBlock(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int coverage = text.indexOf("---KEEL_COVERAGE---");
        if (coverage >= 0) {
            text = text.substring(0, coverage).trim();
        }
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                text = text.substring(firstNl + 1, lastFence).trim();
            }
        }
        return text;
    }

    public static String extractCoverageNotes(String raw) {
        if (raw == null) {
            return "";
        }
        int marker = raw.indexOf("---KEEL_COVERAGE---");
        if (marker < 0) {
            return "";
        }
        return raw.substring(marker + "---KEEL_COVERAGE---".length()).trim();
    }

    private static Map<String, Integer> mapHeader(String[] header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = normalizeHeader(header[i]);
            if (!key.isEmpty()) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static void validateRequired(Map<String, Integer> cols) {
        List<String> required = List.of(
                "TCID", "TITLE", "STEPS", "EXPECTEDRESULT", "KEELPATH");
        for (String col : required) {
            if (!cols.containsKey(col)) {
                throw new IllegalArgumentException("Missing CSV column: " + col);
            }
        }
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String cell(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return "";
        }
        return row[index] == null ? "" : row[index];
    }

    private static boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
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

    /** Fixes LLM column shift when credentials land in KeelPath or TestData holds AUTOMATE. */
    private static String[] realignKeelColumns(String visualAssertion, String testData, String keelRaw) {
        visualAssertion = visualAssertion == null ? "" : visualAssertion;
        testData = testData == null ? "" : testData;
        keelRaw = keelRaw == null ? "" : keelRaw.trim();

        if (isKeelPathToken(keelRaw)) {
            return new String[]{visualAssertion, testData, keelRaw};
        }
        if (keelRaw.isBlank() && isKeelPathToken(testData)) {
            return new String[]{"", visualAssertion, testData.trim()};
        }
        if (isKeelPathToken(testData)) {
            return new String[]{visualAssertion, keelRaw, testData.trim()};
        }
        if (!keelRaw.isBlank() && looksLikeTestData(keelRaw)) {
            String mergedTestData = keelRaw;
            if (!testData.isBlank() && !isKeelPathToken(testData)) {
                mergedTestData = testData + "\n" + keelRaw;
            }
            return new String[]{visualAssertion, mergedTestData, "AUTOMATE"};
        }
        return new String[]{visualAssertion, testData, keelRaw};
    }

    private static boolean looksLikeTestData(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return raw.contains("@")
                || lower.contains("password")
                || lower.contains("username")
                || lower.contains("example.com")
                || lower.contains("wrongpass");
    }

    static List<String[]> parseRows(String csv) {
        List<String[]> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                rows.add(current.toArray(new String[0]));
                current = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !current.isEmpty() || inQuotes) {
            current.add(field.toString());
            rows.add(current.toArray(new String[0]));
        }
        return rows;
    }

    static String csvCell(String value) {
        String s = value == null ? "" : value;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static Map<String, Integer> countByKeelPath(List<ManualTestCase> cases) {
        Map<String, Integer> counts = new HashMap<>();
        for (ManualTestCase tc : cases) {
            String key = tc.keelPath().isBlank() ? "EXECUTE" : tc.keelPath();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }
}
