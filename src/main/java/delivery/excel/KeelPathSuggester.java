package delivery.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Suggests KeelPath from case content (capability-manifest heuristics).
 * Prefer EXECUTE when unsure between AUTOMATE and EXECUTE.
 */
public final class KeelPathSuggester {
    private KeelPathSuggester() {
    }

    public record Suggestion(String suggestedKeelPath, List<String> alternatives, String reason) {
        public Suggestion {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
            reason = reason == null ? "" : reason;
        }
    }

    public static Suggestion suggest(ManualTestCase tc) {
        if (tc == null) {
            return new Suggestion("EXECUTE", List.of("AUTOMATE", "MANUAL"), "empty case");
        }
        String blob = lower(
                tc.title(), tc.steps(), tc.expectedResult(), tc.tags(), tc.visualAssertion(), tc.preconditions());
        if (looksManual(blob)) {
            return new Suggestion("MANUAL", List.of("EXECUTE"), "upload/captcha/otp/native hardware cues");
        }
        boolean hasVisual = tc.visualAssertion() != null && !tc.visualAssertion().isBlank();
        int stepCount = GenerateAuthoringRules.splitNumberedSteps(tc.steps() == null ? "" : tc.steps()).size();
        if (hasVisual && stepCount <= 3) {
            return new Suggestion("VISION_ONLY", List.of("EXECUTE", "AUTOMATE"), "visual assert with few interaction steps");
        }
        if (hasVisual) {
            return new Suggestion("EXECUTE", List.of("VISION_ONLY", "AUTOMATE"), "visual assert with multi-step UI");
        }
        if (looksDynamic(blob)) {
            return new Suggestion("EXECUTE", List.of("AUTOMATE"), "timing/live-exploration cues");
        }
        if (looksLinearUi(blob)) {
            return new Suggestion("AUTOMATE", List.of("EXECUTE"), "linear click/type/navigate flow");
        }
        return new Suggestion("EXECUTE", List.of("AUTOMATE", "MANUAL"), "default when unsure");
    }

    /** Fill blank keelPath only; leave explicit values untouched. */
    public static List<ManualTestCase> applyToBlank(List<ManualTestCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return cases == null ? List.of() : cases;
        }
        List<ManualTestCase> out = new ArrayList<>(cases.size());
        for (ManualTestCase tc : cases) {
            if (tc == null) {
                continue;
            }
            if (tc.keelPath() != null && !tc.keelPath().isBlank()) {
                out.add(tc);
                continue;
            }
            Suggestion s = suggest(tc);
            out.add(new ManualTestCase(
                    tc.tcId(), tc.title(), tc.preconditions(), tc.steps(), tc.expectedResult(),
                    tc.priority(), tc.tags(), tc.visualAssertion(), tc.testData(),
                    s.suggestedKeelPath(), tc.callBefore()));
        }
        return out;
    }

    private static boolean looksManual(String blob) {
        return containsAny(blob,
                "choose file", "file upload", "attach file", "drag and drop", "drag-and-drop",
                "captcha", "otp", "magic link", "sms verification", "bluetooth", "printer",
                "native os", "database-only", "api-only");
    }

    private static boolean looksDynamic(String blob) {
        return containsAny(blob, "wait for", "eventually", "toast", "spinner", "loading",
                "race", "retry until", "dynamic", "explore");
    }

    private static boolean looksLinearUi(String blob) {
        return containsAny(blob, "click ", "enter in", "type ", "select ", "open the", "navigate", "/login");
    }

    private static boolean containsAny(String blob, String... needles) {
        for (String n : needles) {
            if (blob.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null) {
                sb.append(' ').append(p.toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
