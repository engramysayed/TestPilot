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
            Pattern.compile("^ng-?\\w*-?\\d+$"),
            // ASP.NET WebForms client ids/names: ctl00_MainContent_txtUser, ctl00$MainContent$txtUser
            Pattern.compile("^ctl\\d+[_$].*"),
            // JSF: j_idt12:email, form:j_id_3:input, j_id5
            Pattern.compile("(^|.*[:_])j_id(t|_)?\\d+.*"));

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

    /**
     * A {@code name} attribute minted by a server framework from the control tree (ASP.NET {@code ctl00$…},
     * JSF {@code j_idt12:…}) or otherwise generated. These change whenever the page layout changes, so they
     * must not be treated as stable even though they repeat across page loads.
     */
    public static boolean looksGeneratedName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (v.contains("$") && v.toLowerCase(Locale.ROOT).matches("^ctl\\d+\\$.*")) {
            return true;
        }
        if (v.matches("(^|.*[:$])j_id(t|_)?\\d+.*")) {
            return true;
        }
        // Several naming-container hops (a:b:c / a$b$c) are layout-coupled even without ctl/j_id.
        if (v.chars().filter(c -> c == '$').count() >= 2 || v.chars().filter(c -> c == ':').count() >= 2) {
            return true;
        }
        return looksGenerated(v);
    }

    /**
     * Last human segment of a framework-composed name/id ({@code ctl00$MainContent$txtUser} → {@code txtUser},
     * {@code loginForm:j_idt12:email} → {@code email}); empty when none is usable.
     */
    public static String humanTailSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.trim().split("[$:]");
        String tail = parts[parts.length - 1];
        if (parts.length < 2 || tail.isBlank() || looksGenerated(tail) || tail.matches("j_id(t|_)?\\d+")) {
            return "";
        }
        return tail;
    }

    private static final Pattern CSS_MODULE = Pattern.compile("^[A-Za-z][\\w-]*_[\\w-]+__[A-Za-z0-9_-]{5,}$");
    private static final Pattern CSS_IN_JS_PREFIX = Pattern.compile(
            "^(css|sc|jss|emotion|svelte|astro|tw-[a-z0-9]{6,}|makeStyles|jsx)-[A-Za-z0-9_-]+$");

    /**
     * Class token produced by a build tool rather than written by a developer:
     * CSS modules ({@code Button_root__a8f3k}), emotion ({@code css-1x2y3z}), styled-components
     * ({@code sc-bdVaJa} and its random partner {@code kXyZaB}), JSS ({@code jss123}),
     * makeStyles ({@code makeStyles-root-12}), Svelte ({@code svelte-1abc2de}).
     */
    public static boolean looksGeneratedClass(String cls) {
        if (cls == null || cls.isBlank()) {
            return false;
        }
        String c = cls.trim();
        if (CSS_MODULE.matcher(c).matches() || CSS_IN_JS_PREFIX.matcher(c).matches()) {
            return true;
        }
        if (c.matches("^(jss|ng-c|ng-tns-c)\\d+.*")) {
            return true;
        }
        // styled-components partner class: 5–8 letters, several case switches, no separator (e.g. kXyZaB, hYbBaG)
        if (c.matches("^[A-Za-z]{5,8}$")) {
            int switches = 0;
            for (int i = 1; i < c.length(); i++) {
                if (Character.isUpperCase(c.charAt(i)) != Character.isUpperCase(c.charAt(i - 1))) {
                    switches++;
                }
            }
            if (switches >= 5) { // myNavBar has 4; kXyZaB / hYbBaG have 5
                return true;
            }
        }
        // hash blob with digits mixed in (e.g. a8f3kq, x1y2z3)
        String compact = c.replaceAll("[^A-Za-z0-9]", "");
        return compact.length() >= 5 && compact.matches(".*\\d.*") && compact.matches(".*[A-Za-z].*")
                && !hasVowelRun(compact);
    }

    private static boolean hasVowelRun(String s) {
        // human words have letter runs ≥3 with a vowel; hashes rarely do
        var m = LETTER_RUN.matcher(s);
        while (m.find()) {
            String w = m.group();
            if (w.length() >= 3 && hasVowel(w)) {
                return true;
            }
        }
        return false;
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
