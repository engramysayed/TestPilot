package delivery.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Semantic quality rules for LLM-generated manual test cases — catches patterns
 * that pass structural gate checks but fail Execute (ambiguous fields, empty-field drift).
 */
public final class GenerateAuthoringRules {
    private static final Pattern NUMBERED_STEP = Pattern.compile("^\\s*\\d+\\.\\s*(.+)$");
    private static final Pattern VAGUE_ASSERT = Pattern.compile(
            "(?i)confirm\\s+a\\s+clear\\s+validation/error\\s+state|"
                    + "validation/error\\s+state\\s+is\\s+shown|"
                    + "clear\\s+validation/error\\s+state|"
                    + "clear\\s+validation(?:/|\\s+or\\s+)error(?:\\s+message|\\s+state)?|"
                    + "validation(?:/|\\s+or\\s+)error(?:\\s+message|\\s+state)?|"
                    + "confirm\\s+(?:a\\s+)?clear\\s+validation(?:/|\\s+or\\s+)error\\s+message|"
                    + "verify\\s+an?\\s+error\\s+appears|"
                    + "show\\s+an?\\s+error(?:\\s+message)?|"
                    + "user\\s+is\\s+notified|"
                    + "validation\\s+message\\s+is\\s+shown|"
                    + "an?\\s+error(?:\\s+message)?\\s+(?:is\\s+)?(?:shown|displayed|appears)");
    private static final Pattern STANDALONE_PHONE_FIELD = Pattern.compile(
            "(?i)enter\\s+in\\s+the\\s+phone\\s+field");
    private static final Pattern STANDALONE_EMAIL_FIELD = Pattern.compile(
            "(?i)enter\\s+in\\s+the\\s+email\\s+field(?!\\s+or\\s+phone)");
    private static final Pattern LEAVE_EMPTY = Pattern.compile("(?i)leave\\s+the\\s+.+\\s+empty");
    private static final Pattern ENTER_STEP = Pattern.compile("(?i)^enter\\s+");
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']{2,120})[\"']");

    private GenerateAuthoringRules() {
    }

    public static void validate(ManualTestCase tc, String baseUrl, List<String> errors) {
        if (tc == null || errors == null) {
            return;
        }
        String tcId = tc.tcId() == null ? "" : tc.tcId().trim();
        String steps = ExcelStepText.normalizeMultiline(tc.steps() == null ? "" : tc.steps());
        String testData = ExcelStepText.normalizeMultiline(tc.testData() == null ? "" : tc.testData());
        String title = tc.title() == null ? "" : tc.title();
        String expected = tc.expectedResult() == null ? "" : tc.expectedResult();
        String context = (title + " " + expected).toLowerCase(Locale.ROOT);

        checkVagueAssertSteps(tcId, steps, errors);
        checkLoginFieldLabels(tcId, steps, title + " " + expected, errors);
        checkLeaveEmptyTestData(tcId, steps, testData, errors);
        checkEmptyFieldConsistency(tcId, steps, testData, context, errors);
    }

    static void checkLeaveEmptyTestData(String tcId, String steps, String testData, List<String> errors) {
        List<String> stepLines = splitNumberedSteps(steps);
        List<String> dataLines = splitTestDataLines(testData, stepLines.size());
        for (int i = 0; i < stepLines.size(); i++) {
            String step = stepLines.get(i);
            if (!LEAVE_EMPTY.matcher(step).find() && !isKeepEmptyStep(step)) {
                continue;
            }
            String dataLine = i < dataLines.size() ? dataLines.get(i).trim() : "";
            if (!dataLine.isEmpty()) {
                errors.add("tcId '" + tcId + "': leave-empty step requires a blank TestData line "
                        + "(step " + (i + 1) + ")");
            }
        }
    }

    private static boolean isKeepEmptyStep(String step) {
        if (step == null || step.isBlank()) {
            return false;
        }
        String lower = step.toLowerCase(Locale.ROOT);
        if ((lower.contains("keep") || lower.contains("leave")) && lower.contains("empty")) {
            return true;
        }
        if (lower.contains("leave") && lower.contains("blank")) {
            return true;
        }
        if (lower.contains("do not fill") || lower.contains("don't fill") || lower.contains("dont fill")) {
            return true;
        }
        return lower.contains("skip the") && lower.contains("field");
    }

    static void checkVagueAssertSteps(String tcId, String steps, List<String> errors) {
        for (String line : steps.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (VAGUE_ASSERT.matcher(trimmed).find() && !QUOTED.matcher(trimmed).find()) {
                errors.add("tcId '" + tcId + "': vague assert step — quote the exact UI error message "
                        + "(avoid 'clear validation/error state')");
                return;
            }
        }
    }

    static void checkLoginFieldLabels(
            String tcId,
            String steps,
            String titleAndExpected,
            List<String> errors
    ) {
        boolean combinedIdentifier = mentionsCombinedLoginIdentifier(titleAndExpected, steps);
        for (String line : steps.split("\n")) {
            String stepText = numberedStepText(line);
            if (stepText.isEmpty()) {
                continue;
            }
            if (STANDALONE_PHONE_FIELD.matcher(stepText).find()) {
                if (combinedIdentifier || !hasSeparatePhoneFieldHint(titleAndExpected, steps)) {
                    errors.add("tcId '" + tcId + "': use 'Enter in the Email or phone field' — "
                            + "standalone 'Phone field' is ambiguous when login uses one combined box");
                }
            }
            if (combinedIdentifier && STANDALONE_EMAIL_FIELD.matcher(stepText).find()) {
                errors.add("tcId '" + tcId + "': use 'Email or phone field' — "
                        + "standalone 'Email field' is ambiguous when requirements reference email or phone together");
            }
        }
    }

    /** True when title, expected, or error messages imply one identifier field for email and phone. */
    static boolean mentionsCombinedLoginIdentifier(String titleAndExpected, String steps) {
        return containsCombinedIdentifierPhrase(titleAndExpected) || containsCombinedIdentifierPhrase(steps);
    }

    /** True when requirements clearly describe a dedicated phone field (separate from email). */
    static boolean hasSeparatePhoneFieldHint(String titleAndExpected, String steps) {
        String blob = ((titleAndExpected == null ? "" : titleAndExpected) + " "
                + (steps == null ? "" : steps)).toLowerCase(Locale.ROOT);
        return blob.contains("phone field") && !containsCombinedIdentifierPhrase(blob);
    }

    static boolean containsCombinedIdentifierPhrase(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("email or phone")
                || lower.contains("phone or email")
                || lower.contains("email/phone")
                || lower.contains("phone/email")
                || lower.contains("email and phone")
                || lower.contains("phone and email")
                || lower.contains("email or mobile")
                || lower.contains("mobile or email");
    }

    static void checkEmptyFieldConsistency(
            String tcId,
            String steps,
            String testData,
            String titleAndExpectedLower,
            List<String> errors
    ) {
        List<String> stepLines = splitNumberedSteps(steps);
        List<String> dataLines = splitTestDataLines(testData, stepLines.size());

        if (mentionsEmpty(titleAndExpectedLower, "email")) {
            validateEmptyField(tcId, stepLines, dataLines, "email", errors);
        }
        if (mentionsEmpty(titleAndExpectedLower, "phone")) {
            validateEmptyField(tcId, stepLines, dataLines, "phone", errors);
        }
        if (mentionsEmpty(titleAndExpectedLower, "password")) {
            validateEmptyField(tcId, stepLines, dataLines, "password", errors);
        }
    }

    private static void validateEmptyField(
            String tcId,
            List<String> stepLines,
            List<String> dataLines,
            String fieldKind,
            List<String> errors
    ) {
        for (int i = 0; i < stepLines.size(); i++) {
            String step = stepLines.get(i).toLowerCase(Locale.ROOT);
            if (!isEnterStepForField(step, fieldKind)) {
                continue;
            }
            if (!LEAVE_EMPTY.matcher(stepLines.get(i)).find()
                    && !step.contains("empty value")
                    && !step.contains("leave")) {
                errors.add("tcId '" + tcId + "': empty " + fieldKind
                        + " case must use 'Leave the … field empty' (not 'Enter in the … field')");
            }
            String dataLine = i < dataLines.size() ? dataLines.get(i).trim() : "";
            if (!dataLine.isEmpty()) {
                errors.add("tcId '" + tcId + "': empty " + fieldKind
                        + " case requires a blank TestData line for that step (step " + (i + 1) + ")");
            }
        }
    }

    static boolean mentionsEmpty(String text, String fieldKind) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("empty " + fieldKind)
                || lower.contains(fieldKind + " is empty")
                || lower.contains("empty " + fieldKind + " number");
    }

    public static boolean isEnterStepForField(String stepLower, String fieldKind) {
        if (!ENTER_STEP.matcher(stepLower).find() && !stepLower.contains("leave the")) {
            return false;
        }
        return switch (fieldKind) {
            case "email" -> stepLower.contains("email");
            case "phone" -> stepLower.contains("phone");
            case "password" -> stepLower.contains("password");
            default -> false;
        };
    }

    static boolean isStandalonePhoneFieldStep(String stepText) {
        return stepText != null && STANDALONE_PHONE_FIELD.matcher(stepText).find();
    }

    static boolean isStandaloneEmailFieldStep(String stepText) {
        return stepText != null && STANDALONE_EMAIL_FIELD.matcher(stepText).find();
    }

    public static boolean alreadyLeaveEmpty(String stepText) {
        if (stepText == null) return false;
        String step = stepText.toLowerCase(java.util.Locale.ROOT);
        return LEAVE_EMPTY.matcher(stepText).find() || step.contains("empty value") || step.contains("leave");
    }

    static boolean isPlaceholder(String value) {
        String v = value.trim();
        return v.startsWith("<") && v.endsWith(">");
    }

    public static List<String> splitNumberedSteps(String steps) {
        List<String> out = new ArrayList<>();
        if (steps == null || steps.isBlank()) {
            return out;
        }
        for (String line : steps.split("\n")) {
            String text = numberedStepText(line);
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    public static List<String> splitTestDataLines(String testData, int stepCount) {
        List<String> raw = new ArrayList<>();
        if (testData != null && !testData.isBlank()) {
            for (String line : testData.split("\n", -1)) {
                raw.add(line);
            }
        }
        while (raw.size() < stepCount) {
            raw.add("");
        }
        if (raw.size() > stepCount) {
            return raw.subList(0, stepCount);
        }
        return raw;
    }

    static String numberedStepText(String line) {
        if (line == null) {
            return "";
        }
        var m = NUMBERED_STEP.matcher(line.trim());
        return m.matches() ? m.group(1).trim() : "";
    }
}
