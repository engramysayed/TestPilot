package delivery.excel;

import delivery.portal.model.KeelPath;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe deterministic repairs for pasted JSON/CSV test cases before quality validation.
 * Only fences, literal {@code \n}, trim, and valid KeelPath casing — never invents field labels.
 */
public final class TcImportRepair {
    private static final Pattern OPEN_FENCE = Pattern.compile("^```[a-zA-Z0-9_-]*\\r?\\n?");
    private static final Pattern CLOSE_FENCE = Pattern.compile("\\r?\\n?```\\s*$");
    private static final Pattern NUMBERED_TOKEN = Pattern.compile("\\d+\\.\\s");
    private static final Pattern SMASHED_STEP_SPLIT = Pattern.compile("(?<=\\S)\\s+(?=\\d+\\.\\s)");

    private TcImportRepair() {
    }

    public static String stripMarkdownFences(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return raw;
        }
        trimmed = OPEN_FENCE.matcher(trimmed).replaceFirst("");
        trimmed = CLOSE_FENCE.matcher(trimmed).replaceFirst("");
        return trimmed;
    }

    public static String repairMultilineField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = ExcelStepText.normalizeMultiline(value);
        return splitSmashedNumberedSteps(normalized);
    }

    public static List<ManualTestCase> repairCases(List<ManualTestCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        List<ManualTestCase> repaired = new ArrayList<>(cases.size());
        for (ManualTestCase testCase : cases) {
            if (testCase != null) {
                repaired.add(repairCase(testCase));
            }
        }
        return repaired;
    }

    private static ManualTestCase repairCase(ManualTestCase testCase) {
        String tcId = testCase.tcId() == null ? "" : testCase.tcId().trim();
        return new ManualTestCase(
                tcId,
                testCase.title(),
                testCase.preconditions(),
                repairMultilineField(testCase.steps()),
                repairMultilineField(testCase.expectedResult()),
                testCase.priority(),
                testCase.tags(),
                testCase.visualAssertion(),
                repairMultilineField(testCase.testData()),
                repairKeelPath(testCase.keelPath()),
                testCase.callBefore());
    }

    private static String repairKeelPath(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            return KeelPath.parse(trimmed).name();
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }

    private static String splitSmashedNumberedSteps(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (countNumberedTokens(line) > 1) {
                line = SMASHED_STEP_SPLIT.matcher(line).replaceAll("\n");
            }
            if (i > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    private static int countNumberedTokens(String line) {
        Matcher matcher = NUMBERED_TOKEN.matcher(line);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
