package delivery.authoring;

import delivery.excel.ExcelStepText;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pairs each action step with its expected cell. Blank expected → no assert.
 * An expected sitting on an empty step (or leftover numbered/and line) folds onto
 * the previous real step so there is never an assert without an action.
 */
public final class StepExpectedPlanner {
    private StepExpectedPlanner() {
    }

    public record ActionRow(String step, String testData, List<String> expectedClauses) {
    }

    public static List<ActionRow> rows(ManualTestCase tc) {
        String steps = tc == null ? "" : ExcelStepText.normalizeMultiline(tc.steps());
        String expected = tc == null ? "" : ExcelStepText.normalizeMultiline(tc.expectedResult());
        String testData = tc == null ? "" : ExcelStepText.normalizeMultiline(tc.testData());
        String[] stepLines = steps.split("\n", -1);
        String[] dataLines = testData.split("\n", -1);
        List<String> expectedCells = ExpectedClauseSplitter.rows(expected);
        List<ActionRow> actions = new ArrayList<>();
        for (int i = 0; i < stepLines.length; i++) {
            String step = StepIntentBinder.stripStepNumber(stepLines[i]);
            String data = i < dataLines.length ? dataLines[i].trim() : "";
            String cell = i < expectedCells.size() ? expectedCells.get(i) : "";
            if (!isActionStep(step)) {
                if (!cell.isBlank() && !actions.isEmpty()) {
                    mergeExpected(actions, cell);
                }
                continue;
            }
            actions.add(new ActionRow(step, data, new ArrayList<>(ExpectedClauseSplitter.clauses(cell))));
        }
        for (int i = stepLines.length; i < expectedCells.size(); i++) {
            if (!actions.isEmpty()) {
                mergeExpected(actions, expectedCells.get(i));
            }
        }
        return actions;
    }

    static boolean isActionStep(String step) {
        if (step == null || step.isBlank()) {
            return false;
        }
        String t = step.trim();
        if (t.equalsIgnoreCase("step action") || t.equalsIgnoreCase("step")) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("post-login") || lower.startsWith("do not ")) {
            return false;
        }
        return StepIntentBinder.classify(lower) != null
                && !StepIntentBinder.isNavigationalLoginOrUrl(lower)
                && !StepIntentBinder.isOpenPathNavigateIntent(t);
    }

    static boolean looksLikeOutcome(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.length() <= 3 || t.equalsIgnoreCase("ok") || t.equalsIgnoreCase("details")
                || t.equals("—") || t.equals("-")) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (StepIntentBinder.looksLikeAssert(lower)) {
            return true;
        }
        if (t.contains("\"") || t.contains("'")) {
            return true;
        }
        if (lower.contains("displayed") || lower.contains("expands") || lower.contains("section")) {
            return true;
        }
        return t.length() >= 20 && (lower.contains(" is ") || lower.contains(" are "));
    }

    private static void mergeExpected(List<ActionRow> actions, String cell) {
        ActionRow last = actions.get(actions.size() - 1);
        last.expectedClauses().addAll(ExpectedClauseSplitter.clauses(cell));
    }
}
