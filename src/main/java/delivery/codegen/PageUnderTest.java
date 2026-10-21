package delivery.codegen;

import delivery.job.TcOutcome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Which screen/page a generated test primarily exercises (from proven steps, not Excel title). */
public final class PageUnderTest {
    private PageUnderTest() {
    }

    public static String primaryPageName(TcOutcome outcome) {
        if (outcome == null) {
            return "Page";
        }
        String fromBody = primaryPageFromBody(outcome.provenSteps());
        if (fromBody != null) {
            return fromBody;
        }
        String fromSetup = dominantPage(outcome.setupSteps(), false);
        if (fromSetup != null) {
            return fromSetup;
        }
        String fromLogin = dominantPage(outcome.loginSteps(), false);
        if (fromLogin != null) {
            return fromLogin;
        }
        return "Page";
    }

    /**
     * Prefer the last non-login body step (destination screen), else step-count winner on canonical stems.
     */
    static String primaryPageFromBody(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        String lastNonLogin = null;
        for (ProvenStep s : steps) {
            if (s == null) {
                continue;
            }
            String p = s.pageName();
            if (p == null || p.isBlank() || CodegenSmellCheck.isDumpPageName(p)) {
                continue;
            }
            if (!isLoginPageName(p)) {
                lastNonLogin = p.trim();
            }
        }
        if (lastNonLogin != null) {
            return lastNonLogin;
        }
        return dominantPage(steps, true);
    }

    static boolean isLoginPageName(String pageName) {
        if (pageName == null || pageName.isBlank()) {
            return false;
        }
        String stem = CodegenNaming.pageTitleStem(pageName.trim());
        return "Login".equals(stem);
    }

    private static String dominantPage(List<ProvenStep> steps, boolean allowLogin) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> exampleName = new LinkedHashMap<>();
        for (ProvenStep s : steps) {
            if (s == null) {
                continue;
            }
            String p = s.pageName();
            if (p == null || p.isBlank() || CodegenSmellCheck.isDumpPageName(p)) {
                continue;
            }
            String trimmed = p.trim();
            if (!allowLogin && isLoginPageName(trimmed)) {
                continue;
            }
            String key = canonicalStemKey(trimmed);
            counts.merge(key, 1, Integer::sum);
            exampleName.putIfAbsent(key, trimmed);
        }
        if (counts.isEmpty()) {
            return null;
        }
        String bestKey = counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(e -> exampleName.getOrDefault(e.getKey(), "")))
                .map(Map.Entry::getKey)
                .orElse(null);
        return bestKey == null ? null : exampleName.get(bestKey);
    }

    /** Count {@code Order-Details} and {@code OrderDetails} as one stem. */
    private static String canonicalStemKey(String pageName) {
        return CodegenNaming.pageTitleStem(pageName).toLowerCase(java.util.Locale.ROOT);
    }
}
