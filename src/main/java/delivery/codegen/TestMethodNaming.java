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

    public static String resolve(String title, String tcId, LocalLlmClient client, boolean ollamaEnabled) {
        String deterministic = deterministic(title, tcId);
        if (!ollamaEnabled || client == null) {
            return deterministic;
        }
        try {
            String user = "Title: " + (title == null ? "" : title.trim())
                    + "\nFallback: " + deterministic;
            String raw = client.completeChat(OLLAMA_SYSTEM, user, false);
            String polished = parseOllamaResponse(raw);
            return polished != null ? polished : deterministic;
        } catch (Exception ignored) {
            return deterministic;
        }
    }

    public static String deterministic(String title, String tcId) {
        String[] words = tokenize(title);
        if (words.length == 0) {
            return fallback(tcId);
        }
        int n = Math.min(5, words.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('_');
            }
            String w = words[i].toLowerCase(Locale.ROOT);
            if (w.isEmpty()) {
                continue;
            }
            if (i == 0) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1));
                }
            } else {
                sb.append(w);
            }
        }
        if (sb.isEmpty()) {
            return fallback(tcId);
        }
        return CodegenNaming.sanitizeJavaIdentifier(sb.toString(), fallback(tcId));
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
        if (parts.length > 5) {
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
