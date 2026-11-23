package delivery.codegen;

import java.util.Arrays;
import java.util.Locale;

/**
 * Deterministic title → Java test method name.
 */
public final class TestMethodNaming {

    private TestMethodNaming() {
    }

    public static String deterministicFromPage(String pageName, String tcId) {
        String stem = CodegenNaming.pageTitleStem(pageName);
        if (stem == null || stem.isBlank() || "Page".equals(stem)) {
            return fallback(tcId);
        }
        String raw = stem.substring(0, 1).toLowerCase(Locale.ROOT)
                + (stem.length() > 1 ? stem.substring(1) : "");
        return CodegenNaming.sanitizeJavaIdentifier(raw, fallback(tcId));
    }

    /**
     * Method name mirroring the TC title: {@code should_<snake_case_title>}. Falls back to the
     * page-based name when title is blank, so TCs with no title still get a valid identifier.
     */
    public static String shouldFromTitle(String title, String pageName, String tcId) {
        if (title != null && !title.isBlank()) {
            String[] words = tokenize(title);
            if (words.length > 0) {
                StringBuilder sb = new StringBuilder("should");
                for (String w : words) {
                    if (w.isBlank()) {
                        continue;
                    }
                    sb.append('_').append(w.toLowerCase(Locale.ROOT));
                }
                String candidate = sb.toString();
                if (candidate.length() > 100) {
                    String trimmed = candidate.substring(0, 100);
                    int lastUnderscore = trimmed.lastIndexOf('_');
                    if (lastUnderscore > "should".length()) {
                        trimmed = trimmed.substring(0, lastUnderscore);
                    }
                    candidate = trimmed;
                }
                return CodegenNaming.sanitizeJavaIdentifier(candidate, "should_" + fallback(tcId));
            }
        }
        return deterministicFromPage(pageName, tcId);
    }

    public static String deterministic(String title, String tcId) {
        String[] words = tokenize(title);
        if (words.length == 0) {
            return fallback(tcId);
        }
        int n = words.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (sb.length() > 0) {
                sb.append('_');
            }
            String w = words[i].toLowerCase(Locale.ROOT);
            if (w.isEmpty()) {
                continue;
            }
            if (sb.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1));
                }
            } else {
                sb.append(w);
            }
            if (sb.length() > 100) {
                break;
            }
        }
        if (sb.isEmpty()) {
            return fallback(tcId);
        }
        String raw = sb.length() > 100 ? sb.substring(0, 100) : sb.toString();
        return CodegenNaming.sanitizeJavaIdentifier(raw, fallback(tcId));
    }

    private static String[] tokenize(String title) {
        if (title == null || title.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(title.trim().split("[^A-Za-z0-9]+"))
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    private static String fallback(String tcId) {
        String id = tcId == null ? "TC" : tcId.replaceAll("[^A-Za-z0-9_]", "_");
        return CodegenNaming.sanitizeJavaIdentifier("case_" + id, "case_Tc");
    }
}
