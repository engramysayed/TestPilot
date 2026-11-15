package delivery.authoring;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unicode-aware text handling for step ↔ element matching.
 * <ul>
 *   <li>{@link #normalize}: NFKC, lowercase, whitespace collapsed.</li>
 *   <li>{@link #tokens}: splits on non-letter/non-digit (any script), drops stop words, filters short tokens.</li>
 *   <li>{@link #containsToken}: whole-word match that still sees camelCase / kebab / snake compounds.</li>
 * </ul>
 */
public final class IntentText {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    public static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "only",
            "click", "open", "confirm", "verify", "shown", "visible", "page",
            "user", "already", "logged", "enter", "type", "fill", "button",
            "item", "was", "are", "has", "have", "select", "choose", "pick",
            "clicks", "sidebar", "toolbar", "menu");

    private IntentText() {
    }

    public static String normalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    public static List<String> tokens(String text) {
        return tokens(text, 3);
    }

    public static List<String> tokens(String text, int minLatin) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : NON_WORD.split(normalize(text))) {
            if (raw.isEmpty() || raw.length() < minLatin || STOP.contains(raw)) {
                continue;
            }
            if (!out.contains(raw)) {
                out.add(raw);
            }
        }
        return out;
    }

    /**
     * Whole-word containment. Also matches inside camelCase / kebab / snake compounds of
     * locator values ({@code placeOrder}, {@code place-order}) but never as a prefix of a longer word
     * ({@code add} does not match {@code address}).
     */
    public static boolean containsToken(String hay, String token) {
        if (hay == null || token == null || token.isBlank()) {
            return false;
        }
        String t = normalize(token);
        if (t.isEmpty()) {
            return false;
        }
        String spaced = hay
                .replaceAll("([\\p{Ll}\\p{N}])(\\p{Lu})", "$1 $2")
                .replace('_', ' ').replace('-', ' ');
        for (String w : NON_WORD.split(normalize(spaced))) {
            if (w.equals(t)) {
                return true;
            }
        }
        return false;
    }

    /** Phrase (one or more words) present on word boundaries. */
    public static boolean containsPhrase(String hay, String phrase) {
        if (hay == null || phrase == null || phrase.isBlank()) {
            return false;
        }
        String h = " " + String.join(" ", NON_WORD.split(normalize(hay))) + " ";
        String p = " " + String.join(" ", NON_WORD.split(normalize(phrase))).trim() + " ";
        return p.trim().length() > 0 && h.contains(p);
    }
}
