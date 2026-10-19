package delivery.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strips filler words from leftover Expected clauses before quoting distinctive names. */
public final class ExpectedQuoteFilter {
    public static final Set<String> FILLERS = Set.of(
            "area", "button", "label", "title", "page", "section", "field", "menu",
            "item", "list", "visible", "shown", "displayed");

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "is", "are", "was", "were", "with", "in", "on", "at", "to", "for");

    private ExpectedQuoteFilter() {
    }

    /** Lowercase name tokens for {@code clause}, fillers and stopwords removed; quoted spans skipped. */
    public static List<String> tokens(String clause) {
        List<String> words = nameWords(clause);
        List<String> out = new ArrayList<>();
        for (String w : words) {
            out.add(w.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * True when the leftover name is worth quoting: two or more tokens, or one token of length
     * at least three with an uppercase letter in {@code remainderDisplay}.
     */
    public static boolean distinctive(List<String> tokens, String remainderDisplay) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        if (tokens.size() >= 2) {
            return true;
        }
        String only = tokens.get(0);
        if (only.length() < 3) {
            return false;
        }
        if (remainderDisplay == null) {
            return false;
        }
        for (int i = 0; i < remainderDisplay.length(); i++) {
            if (Character.isUpperCase(remainderDisplay.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Distinctive phrase with original casing (filler and stopwords removed). */
    static String remainderDisplay(String clause) {
        List<String> words = nameWords(clause);
        if (words.isEmpty()) {
            return "";
        }
        return String.join(" ", words);
    }

    private static List<String> nameWords(String clause) {
        List<String> raw = new ArrayList<>();
        scanWords(clause, raw::add);
        List<String> out = new ArrayList<>();
        for (String w : raw) {
            String lower = w.toLowerCase(Locale.ROOT);
            if (FILLERS.contains(lower) || STOPWORDS.contains(lower)) {
                continue;
            }
            out.add(w);
        }
        return out;
    }

    static void scanWords(String clause, WordConsumer consumer) {
        int i = 0;
        while (i < clause.length()) {
            char c = clause.charAt(i);
            if (c == '\'' || c == '"') {
                int end = clause.indexOf(c, i + 1);
                i = end < 0 ? clause.length() : end + 1;
                continue;
            }
            if (isWordChar(c)) {
                int start = i;
                while (i < clause.length() && isWordChar(clause.charAt(i))) {
                    i++;
                }
                consumer.accept(clause.substring(start, i));
                continue;
            }
            i++;
        }
    }

    private static boolean isWordChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    interface WordConsumer {
        void accept(String word);
    }
}
