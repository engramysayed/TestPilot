package delivery.excel;

import delivery.portal.model.KeelPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates LLM-generated manual test cases before save. Returns human-readable errors
 * (empty list = pass).
 */
public final class GenerateQualityGate {
    private static final Pattern TC_ID = Pattern.compile("^TC_(\\d+|[A-Z0-9_]+)$");

    private GenerateQualityGate() {
    }

    public static List<String> validate(List<ManualTestCase> cases) {
        return validate(cases, null);
    }

    public static List<String> validate(List<ManualTestCase> cases, String baseUrl) {
        if (cases == null || cases.isEmpty()) {
            return List.of("No test cases generated");
        }
        List<String> errors = new ArrayList<>();
        for (ManualTestCase tc : cases) {
            if (tc == null) {
                errors.add("Null test case row");
                continue;
            }
            validateOne(tc, errors);
            GenerateAuthoringRules.validate(tc, baseUrl, errors);
        }
        return List.copyOf(errors);
    }

    public static IllegalArgumentException failureException(List<String> errors) {
        return new IllegalArgumentException("QUALITY_GATE: " + String.join("; ", errors));
    }

    public static boolean isQualityGateFailure(IllegalArgumentException e) {
        return e.getMessage() != null && e.getMessage().startsWith("QUALITY_GATE:");
    }

    public static String qualityGateDetail(IllegalArgumentException e) {
        return e.getMessage().substring("QUALITY_GATE:".length()).trim();
    }

    private static void validateOne(ManualTestCase tc, List<String> errors) {
        String tcId = tc.tcId() == null ? "" : tc.tcId().trim();
        if (!TC_ID.matcher(tcId).matches()) {
            errors.add("Invalid tcId '" + tcId + "': must match TC_<digits> or TC_<ALNUM_UNDERSCORE>");
        } else if (looksLikeStepProseInTcId(tcId)) {
            errors.add("tcId '" + tcId + "' looks like step prose, not an identifier");
        }

        String rawSteps = tc.steps() == null ? "" : tc.steps();
        String normalizedSteps = ExcelStepText.normalizeMultiline(rawSteps).trim();
        if (normalizedSteps.isEmpty()) {
            errors.add("tcId '" + tcId + "': steps are blank after normalize");
        } else if (hasLiteralBackslashNWithoutRealNewlines(rawSteps)) {
            errors.add("tcId '" + tcId
                    + "': steps contain literal \\n without real newlines — use actual line breaks");
        }

        String keelRaw = tc.keelPath() == null ? "" : tc.keelPath().trim();
        if (!keelRaw.isBlank()) {
            try {
                KeelPath.parse(keelRaw);
            } catch (IllegalArgumentException e) {
                errors.add("tcId '" + tcId + "': " + e.getMessage());
            }
        }
    }

    static boolean looksLikeStepProseInTcId(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            return false;
        }
        String id = tcId.trim();
        if (id.contains(". ")) {
            return true;
        }
        if (id.length() > 32) {
            return true;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (String verb : List.of(" click ", " open ", " verify ", " navigate ", " enter ", " submit ")) {
            if (lower.contains(verb)) {
                return true;
            }
        }
        return id.matches(".*\\s+[a-z].*");
    }

    static boolean hasLiteralBackslashNWithoutRealNewlines(String rawSteps) {
        if (rawSteps == null || rawSteps.isEmpty()) {
            return false;
        }
        if (!rawSteps.contains("\\n")) {
            return false;
        }
        return rawSteps.indexOf('\n') < 0 && rawSteps.indexOf('\r') < 0;
    }
}
