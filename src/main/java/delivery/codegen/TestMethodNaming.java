package delivery.codegen;

import delivery.authoring.LocalLlmClient;

import java.util.Arrays;
import java.util.Locale;

/**
 * Deterministic title → Java test method name; optional Ollama polish (methods only).
 */
public final class TestMethodNaming {
    private static final String OLLAMA_SYSTEM =
            "Return ONE Java test method identifier only, max 5 English words, "
                    + "underscores between words, no explanation or punctuation.";

    private TestMethodNaming() {
    }

    public static String resolveFromPage(
            String pageName, String tcId, LocalLlmClient client, boolean ollamaEnabled) {
        String deterministic = deterministicFromPage(pageName, tcId);
        if (!ollamaEnabled || client == null) {
            return deterministic;
        }
        try {
            String user = "Page: " + (pageName == null ? "" : pageName.trim())
                    + "\nFallback: " + deterministic;
            String raw = client.completeChat(OLLAMA_SYSTEM, user, false);
            String polished = parseOllamaResponse(raw);
            return polished != null ? polished : deterministic;
        } catch (Exception ignored) {
            return deterministic;
        }
    }

    /** @deprecated Excel titles are not used for method names. */
    @Deprecated
    public static String resolve(String title, String tcId, LocalLlmClient client, boolean ollamaEnabled) {
        return resolveFromPage(title, tcId, client, ollamaEnabled);
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

    static String parseOllamaResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("^[\"'`]+|[\"'`]+$", "");
        cleaned = cleaned.replaceAll("[^A-Za-z0-9_\\s]+", " ").trim();
        if (cleaned.isBlank()) {
            return null;
        }
        String[] parts = cleaned.split("[\\s_]+");
        if (parts.length == 0) {
            return null;
        }
        if (parts.length > 11) {
            return null;
        }
        int n = parts.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (parts[i].isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('_');
            }
            String w = parts[i].toLowerCase(Locale.ROOT);
            if (i == 0) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1));
                }
            } else {
                sb.append(w);
            }
        }
        String name = sb.toString();
        if (!CodegenNaming.isValidJavaIdentifier(name)) {
            return null;
        }
        return name;
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
