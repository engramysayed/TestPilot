package delivery.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic repairs for LLM-generated manual test cases before quality validation.
 */
public final class GenerateAuthoringRepair {
    private static final List<String> EMPTY_FIELD_KINDS = List.of("email", "phone", "password");

    private GenerateAuthoringRepair() {
    }

    public static List<ManualTestCase> repair(List<ManualTestCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }

        List<ManualTestCase> repaired = new ArrayList<>(cases.size());
        for (ManualTestCase testCase : cases) {
            repaired.add(repairCase(testCase));
        }
        return repaired;
    }

    private static ManualTestCase repairCase(ManualTestCase testCase) {
        if (testCase == null) {
            return null;
        }

        String steps = testCase.steps();
        if (GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(steps)) {
            steps = steps.replace("\\n", "\n");
        }
        String expectedResult = testCase.expectedResult();
        if (GenerateQualityGate.hasLiteralBackslashNWithoutRealNewlines(expectedResult)) {
            expectedResult = expectedResult.replace("\\n", "\n");
        }

        List<String> stepLines = GenerateAuthoringRules.splitNumberedSteps(steps);
        String titleAndExpected = (testCase.title() == null ? "" : testCase.title())
                + " "
                + (expectedResult == null ? "" : expectedResult);
        boolean stepsChanged = false;
        boolean combinedLogin = GenerateAuthoringRules.mentionsCombinedLoginIdentifier(
                titleAndExpected, steps);

        for (int i = 0; i < stepLines.size(); i++) {
            String step = stepLines.get(i);
            if (combinedLogin
                    && !step.toLowerCase(Locale.ROOT).contains("email or phone")
                    && (GenerateAuthoringRules.isStandalonePhoneFieldStep(step)
                    || GenerateAuthoringRules.isStandaloneEmailFieldStep(step))) {
                String rewritten = step
                        .replaceAll("(?i)Phone field", "Email or phone field")
                        .replaceAll("(?i)Email field", "Email or phone field");
                stepLines.set(i, rewritten);
                stepsChanged = true;
            }
        }

        for (String fieldKind : EMPTY_FIELD_KINDS) {
            if (!GenerateAuthoringRules.mentionsEmpty(titleAndExpected, fieldKind)) {
                continue;
            }
            for (int i = 0; i < stepLines.size(); i++) {
                String step = stepLines.get(i);
                if (GenerateAuthoringRules.isEnterStepForField(
                        step.toLowerCase(Locale.ROOT), fieldKind)
                        && !GenerateAuthoringRules.alreadyLeaveEmpty(step)) {
                    String stepLower = step.toLowerCase(Locale.ROOT);
                    String label = stepLower.contains("email or phone")
                            ? "Email or phone"
                            : switch (fieldKind) {
                                case "email" -> "Email";
                                case "phone" -> "Phone";
                                case "password" -> "Password";
                                default -> fieldKind;
                            };
                    stepLines.set(i, "Leave the " + label + " field empty");
                    stepsChanged = true;
                }
            }
        }

        List<String> dataLines = GenerateAuthoringRules.splitTestDataLines(
                testCase.testData(), stepLines.size());
        boolean testDataChanged = false;
        for (String fieldKind : EMPTY_FIELD_KINDS) {
            if (!GenerateAuthoringRules.mentionsEmpty(titleAndExpected, fieldKind)) {
                continue;
            }
            for (int i = 0; i < stepLines.size(); i++) {
                String dataLine = dataLines.get(i);
                if (GenerateAuthoringRules.isEnterStepForField(
                        stepLines.get(i).toLowerCase(Locale.ROOT), fieldKind)
                        && !dataLine.trim().isEmpty()
                        && !GenerateAuthoringRules.isPlaceholder(dataLine)) {
                    dataLines.set(i, "");
                    testDataChanged = true;
                }
            }
        }

        steps = stepsChanged ? numberedSteps(stepLines) : steps;
        String testData = testDataChanged ? String.join("\n", dataLines) : testCase.testData();
        return new ManualTestCase(
                testCase.tcId(),
                testCase.title(),
                testCase.preconditions(),
                steps,
                expectedResult,
                testCase.priority(),
                testCase.tags(),
                testCase.visualAssertion(),
                testData,
                testCase.keelPath());
    }

    private static String numberedSteps(List<String> stepLines) {
        List<String> numbered = new ArrayList<>(stepLines.size());
        for (int i = 0; i < stepLines.size(); i++) {
            numbered.add((i + 1) + ". " + stepLines.get(i));
        }
        return String.join("\n", numbered);
    }
}
