package delivery.authoring;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether an identifier attribute was written by a developer or minted by a framework
 * at render time. Generated ids ({@code _r_15_}, {@code :r0:}, {@code ember1423}) change on every
 * load, so a locator built on one passes today and breaks tomorrow — such elements must fall
 * through to a CSS selector built on a human attribute instead.
 *
 * <p>An identifier is treated as generated when any of these hold:
 * <ol>
 *   <li>it matches a known framework id shape;</li>
 *   <li>it contains no run of three or more letters, so there is no human word in it;</li>
 *   <li>its only word is an unpronounceable consonant blob;</li>
 *   <li>it is a hex or digit blob, or digits outnumber letters.</li>
 * </ol>
 */
public final class GeneratedIdDetector {
    private static final Pattern UUID_LIKE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{6,}");
    private static final Pattern LETTER_RUN = Pattern.compile("[a-zA-Z]+");
    private static final Pattern HEX_BLOB = Pattern.compile("[0-9a-fA-F]{8,}");

    /** Shapes that identify the framework that minted the id rather than the element's purpose. */
    private static final List<Pattern> FRAMEWORK_SHAPES = List.of(
            Pattern.compile("^_+r_+[0-9a-z]*_*$"),                  // React useId — _r_15_, _r_k_
            Pattern.compile("^:+r[0-9a-z]*:+$"),                    // React 18 — :r0:, :r1a:
            Pattern.compile("^(ember|ext-gen|ext-comp|yui_|gwt-uid|isc_)[\\w-]*\\d+[\\w-]*$"),
            Pattern.compile("^(mui|radix|headlessui|downshift|react-select|react-aria)[-_].*"),
            Pattern.compile("^(svelte|sc|css|jss|emotion)-[0-9a-z]{5,}$"),
            Pattern.compile("^(js|jsc|mount|u)_[0-9a-z]{1,4}(_[0-9a-z]{1,4})*$"),
            Pattern.compile("^ng-?\\w*-?\\d+$"));

    private GeneratedIdDetector() {
    }

    public static boolean looksGenerated(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        String lower = v.toLowerCase(Locale.ROOT);

        if (UUID_LIKE.matcher(v).find() || LONG_DIGIT_RUN.matcher(v).find()) {
            return true;
        }
        for (Pattern shape : FRAMEWORK_SHAPES) {
            if (shape.matcher(lower).matches()) {
                return true;
            }
        }

        String longestWord = longestLetterRun(v);
        if (longestWord.length() < 3) {
            return true;
        }
        if (longestWord.length() >= 5 && !hasVowel(longestWord)) {
            return true;
        }

        String compact = v.replaceAll("[^0-9a-zA-Z]", "");
        if (HEX_BLOB.matcher(compact).matches()) {
            return true;
        }
        long digits = compact.chars().filter(Character::isDigit).count();
        long letters = compact.length() - digits;
        return digits > letters;
    }

    /** Convenience for callers that also need the length/blank checks. */
    public static boolean isTrustworthyIdentifier(String value) {
        return value != null && !value.isBlank() && value.length() < 120 && !looksGenerated(value);
    }

    private static String longestLetterRun(String value) {
        String longest = "";
        var m = LETTER_RUN.matcher(value);
        while (m.find()) {
            if (m.group().length() > longest.length()) {
                longest = m.group();
            }
        }
        return longest;
    }

    private static boolean hasVowel(String word) {
        return word.toLowerCase(Locale.ROOT).chars()
                .anyMatch(c -> c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y');
    }
}
