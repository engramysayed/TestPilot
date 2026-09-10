package delivery.heal;

import delivery.codegen.ProvenStep;
import delivery.excel.GenerateAuthoringRules;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic Excel cell patches from proven heal recovery steps.
 * Does not invent new numbered steps from automationNotes prose.
 */
public final class HealWorkbookPatcher {
    private HealWorkbookPatcher() {
    }

    public record PatchResult(
            ManualTestCase patchedCase,
            List<String> appliedSummaries,
            List<String> unmatchedNotes,
            boolean cellsChanged
    ) {
        public PatchResult {
            appliedSummaries = appliedSummaries == null ? List.of() : List.copyOf(appliedSummaries);
            unmatchedNotes = unmatchedNotes == null ? List.of() : List.copyOf(unmatchedNotes);
        }
    }

    public static PatchResult patch(
            ManualTestCase tc,
            List<ProvenStep> recoverySteps,
            List<String> automationNotes
    ) {
        List<String> unmatched = notesOrEmpty(automationNotes);
        if (tc == null) {
            return new PatchResult(null, List.of(), unmatched, false);
        }

        List<String> stepLines = new ArrayList<>(GenerateAuthoringRules.splitNumberedSteps(tc.steps()));
        List<String> dataLines = new ArrayList<>(
                GenerateAuthoringRules.splitTestDataLines(tc.testData(), Math.max(stepLines.size(), 1)));
        while (dataLines.size() < stepLines.size()) {
            dataLines.add("");
        }
        List<String> applied = new ArrayList<>();
        boolean stepsChanged = false;
        boolean dataChanged = false;

        if (recoverySteps != null) {
            for (ProvenStep step : recoverySteps) {
                if (!isClearOrBlankType(step)) {
                    continue;
                }
                String kind = inferFieldKind(step.locatorValue());
                int idx = findBestStepIndex(stepLines, kind, step.locatorValue());
                if (idx < 0) {
                    continue;
                }
                String current = stepLines.get(idx);
                if (!GenerateAuthoringRules.alreadyLeaveEmpty(current)) {
                    String label = leaveEmptyLabel(current, kind);
                    stepLines.set(idx, "Leave the " + label + " field empty");
                    stepsChanged = true;
                }
                while (dataLines.size() <= idx) {
                    dataLines.add("");
                }
                if (!dataLines.get(idx).isBlank()) {
                    dataLines.set(idx, "");
                    dataChanged = true;
                }
                applied.add("step " + (idx + 1) + ": leave-empty "
                        + leaveEmptyLabel(stepLines.get(idx), kind));
            }
        }

        boolean cellsChanged = stepsChanged || dataChanged;
        ManualTestCase out = tc;
        if (cellsChanged) {
            out = new ManualTestCase(
                    tc.tcId(),
                    tc.title(),
                    tc.preconditions(),
                    numberedSteps(stepLines),
                    tc.expectedResult(),
                    tc.priority(),
                    tc.tags(),
                    tc.visualAssertion(),
                    String.join("\n", dataLines),
                    tc.keelPath(),
                    tc.callBefore());
        }
        return new PatchResult(out, applied, unmatched, cellsChanged);
    }

    static boolean isClearOrBlankType(ProvenStep step) {
        if (step == null || step.action() == null) {
            return false;
        }
        String a = step.action().trim().toLowerCase(Locale.ROOT);
        if ("clear".equals(a)) {
            return true;
        }
        if ("type".equals(a)) {
            return step.value() == null || step.value().isBlank();
        }
        return false;
    }

    static String inferFieldKind(String locatorValue) {
        String h = locatorValue == null ? "" : locatorValue.toLowerCase(Locale.ROOT);
        if (h.contains("password") || h.contains("passwd")) {
            return "password";
        }
        if (h.contains("email") || h.contains("e-mail")) {
            return "email";
        }
        if (h.contains("phone") || h.contains("mobile") || h.contains("tel")) {
            return "phone";
        }
        return "";
    }

    static int findBestStepIndex(List<String> stepLines, String kind, String locatorValue) {
        if (stepLines == null || stepLines.isEmpty()) {
            return -1;
        }
        if (kind != null && !kind.isBlank()) {
            for (int i = 0; i < stepLines.size(); i++) {
                String lower = stepLines.get(i).toLowerCase(Locale.ROOT);
                if (GenerateAuthoringRules.isEnterStepForField(lower, kind)) {
                    return i;
                }
            }
            for (int i = 0; i < stepLines.size(); i++) {
                String lower = stepLines.get(i).toLowerCase(Locale.ROOT);
                if (GenerateAuthoringRules.alreadyLeaveEmpty(stepLines.get(i)) && lower.contains(kind)) {
                    return i;
                }
            }
        }
        String token = (kind == null || kind.isBlank()) ? lastLocatorToken(locatorValue) : kind;
        if (token.isBlank()) {
            return -1;
        }
        for (int i = 0; i < stepLines.size(); i++) {
            if (stepLines.get(i).toLowerCase(Locale.ROOT).contains(token)) {
                return i;
            }
        }
        return -1;
    }

    private static String lastLocatorToken(String locatorValue) {
        if (locatorValue == null || locatorValue.isBlank()) {
            return "";
        }
        String h = locatorValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", " ").trim();
        String[] parts = h.split("\\s+");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String leaveEmptyLabel(String stepText, String kind) {
        String lower = stepText == null ? "" : stepText.toLowerCase(Locale.ROOT);
        if (lower.contains("email or phone")) {
            return "Email or phone";
        }
        return switch (kind == null ? "" : kind) {
            case "email" -> "Email";
            case "phone" -> "Phone";
            case "password" -> "Password";
            default -> {
                if (lower.contains("email")) {
                    yield "Email";
                }
                if (lower.contains("phone")) {
                    yield "Phone";
                }
                if (lower.contains("password")) {
                    yield "Password";
                }
                yield "field";
            }
        };
    }

    private static List<String> notesOrEmpty(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String n : notes) {
            if (n != null && !n.isBlank() && seen.add(n.trim())) {
                out.add(n.trim());
            }
        }
        return out;
    }

    private static String numberedSteps(List<String> stepLines) {
        List<String> numbered = new ArrayList<>(stepLines.size());
        for (int i = 0; i < stepLines.size(); i++) {
            numbered.add((i + 1) + ". " + stepLines.get(i));
        }
        return String.join("\n", numbered);
    }
}
