package delivery.excel;

import delivery.portal.model.KeelPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates LLM-generated manual test cases before save. Returns human-readable errors
 * (empty list = pass).
 */
public final class GenerateQualityGate {

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
        try {
            CallBefore.validateGraph(cases);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
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
        if (!delivery.ir.TcIdentity.isValid(tcId)) {
            errors.add(delivery.ir.TcIdentity.invalidMessage(tcId));
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

    public record GateFailure(
            String rule,
            String tcId,
            String field,
            String message,
            String severity,
            String howToFix
    ) {
        public GateFailure {
            rule = rule == null ? "RULE" : rule;
            tcId = tcId == null ? "" : tcId;
            field = field == null ? "" : field;
            message = message == null ? "" : message;
            severity = severity == null ? "error" : severity;
            howToFix = howToFix == null ? "" : howToFix;
        }

        public Map<String, Object> toErrorMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rule", rule);
            if (!tcId.isBlank()) m.put("tcId", tcId);
            if (!field.isBlank()) m.put("field", field);
            m.put("message", message);
            m.put("severity", severity);
            return m;
        }

        public Map<String, Object> toFailureMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", rule);
            m.put("message", message);
            m.put("howToFix", howToFix.isBlank() ? defaultHowToFix(rule) : howToFix);
            return m;
        }
    }

    public record GateResult(boolean passed, List<GateFailure> failures) {
        public GateResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public List<String> messages() {
            List<String> out = new ArrayList<>();
            for (GateFailure f : failures) {
                out.add(f.message());
            }
            return out;
        }
    }

    public static GateResult validateDetailed(List<ManualTestCase> cases) {
        return validateDetailed(cases, null);
    }

    public static GateResult validateDetailed(List<ManualTestCase> cases, String baseUrl) {
        List<String> raw = validate(cases, baseUrl);
        if (raw.isEmpty()) {
            return new GateResult(true, List.of());
        }
        List<GateFailure> failures = new ArrayList<>();
        for (String msg : raw) {
            failures.add(parseFailure(msg));
        }
        return new GateResult(false, failures);
    }

    /** Structured QUALITY_GATE API map (Frontend COORDINATE_WITH_AE). */
    public static Map<String, Object> toApiMap(GateResult result, List<Map<String, Object>> suggestions) {
        List<GateFailure> failures = result == null ? List.of() : result.failures();
        String message = String.join("; ", result == null ? List.<String>of() : result.messages());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "QUALITY_GATE");
        body.put("code", "QUALITY_GATE");
        body.put("message", message);
        body.put("detail", message);
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> qgFailures = new ArrayList<>();
        for (GateFailure f : failures) {
            errors.add(f.toErrorMap());
            qgFailures.add(f.toFailureMap());
        }
        body.put("errors", errors);
        body.put("suggestions", suggestions == null ? List.of() : suggestions);
        Map<String, Object> qualityGate = new LinkedHashMap<>();
        qualityGate.put("passed", result != null && result.passed());
        qualityGate.put("failures", qgFailures);
        body.put("qualityGate", qualityGate);
        return body;
    }

    public static Map<String, Object> toApiMap(List<String> gateErrors, List<Map<String, Object>> suggestions) {
        List<GateFailure> failures = new ArrayList<>();
        if (gateErrors != null) {
            for (String msg : gateErrors) {
                failures.add(parseFailure(msg));
            }
        }
        return toApiMap(new GateResult(failures.isEmpty(), failures), suggestions);
    }

    static GateFailure parseFailure(String msg) {
        if (msg == null) {
            msg = "";
        }
        String tcId = "";
        String field = "";
        String rule = "AUTHORING";
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("tcid '") || lower.contains("tcid \"")) {
            int start = lower.indexOf("tcid ");
            if (start >= 0) {
                int q1 = msg.indexOf('\'', start);
                if (q1 < 0) q1 = msg.indexOf('"', start);
                if (q1 >= 0) {
                    int q2 = msg.indexOf(msg.charAt(q1), q1 + 1);
                    if (q2 > q1) {
                        tcId = msg.substring(q1 + 1, q2);
                    }
                }
            }
        }
        if (lower.contains("steps")) field = "steps";
        else if (lower.contains("testdata") || lower.contains("test data")) field = "testData";
        else if (lower.contains("keelpath")) field = "keelPath";
        else if (lower.contains("expected")) field = "expectedResult";
        if (lower.contains("vague assert")) rule = "VAGUE_ASSERT";
        else if (lower.contains("leave-empty") || lower.contains("leave the")) rule = "EMPTY_FIELD";
        else if (lower.contains("email or phone") || lower.contains("phone field")) rule = "LOGIN_FIELD_LABEL";
        else if (lower.contains("field_visibility") || lower.contains("visibility")) rule = "FIELD_VISIBILITY";
        else if (lower.contains("backslash")) rule = "LITERAL_NEWLINE";
        else if (lower.contains("no test cases")) rule = "EMPTY_BATCH";
        else if (lower.contains("call before") || lower.contains("callbefore")) rule = "CALL_BEFORE";
        else if (lower.contains("invalid") && lower.contains("tc")) rule = "TC_ID";
        return new GateFailure(rule, tcId, field, msg, "error", defaultHowToFix(rule));
    }

    static String defaultHowToFix(String rule) {
        return switch (rule == null ? "" : rule) {
            case "VAGUE_ASSERT" -> "Quote the exact UI error message in the confirm/assert step.";
            case "EMPTY_FIELD" -> "Use 'Leave the … field empty' and keep the matching TestData line blank.";
            case "LOGIN_FIELD_LABEL" -> "On combined login boxes use 'Email or phone field'.";
            case "FIELD_VISIBILITY" -> "Do not enter a field immediately after asserting it is hidden; add a reveal step.";
            case "LITERAL_NEWLINE" -> "Use real line breaks (or JSON \\n escapes) — not the two-character sequence backslash-n in CSV.";
            case "EMPTY_BATCH" -> "Regenerate with 1–5 complete testCases grounded in the pasted stories.";
            case "TC_ID" -> "Use ids like TC_01; put step text only in the steps field.";
            case "CALL_BEFORE" -> "Fix CallBefore cycles / unknown target ids.";
            default -> "Fix the listed authoring issue and re-import, or run Authoring review.";
        };
    }

}
