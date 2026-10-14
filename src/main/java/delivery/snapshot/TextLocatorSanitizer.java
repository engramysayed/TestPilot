package delivery.snapshot;

import java.util.regex.Pattern;

/**
 * Decides whether a visible text may become a text= locator (contract v2). Text locators end up in
 * generated customer code and IR, so personal or volatile data must never be embedded. Rejected
 * (not masked: a masked literal cannot match). Rules are mirrored in SPEC.md and the golden vectors.
 */
public final class TextLocatorSanitizer {
    static final int MIN_LEN = 2;
    static final int MAX_LEN = 80;
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d(?:[\\s.-]?\\d){5,}");   // phone, card, account, OTP
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b");
    private static final Pattern MONEY = Pattern.compile("(?i)(?:[$€£¥]|\\b(?:egp|usd|eur|sar|aed)\\b)\\s?\\d");
    private static final Pattern DATE_TIME = Pattern.compile("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b|\\b\\d{1,2}:\\d{2}\\b");
    private static final Pattern GREETING = Pattern.compile("(?i)^(hi|hello|welcome|مرحبا)[ ,]");

    private static final Pattern GREETING_NAME = Pattern.compile("(?i)\\b(hi|hello|welcome|مرحبا)([ ,]+)[^\\s,!.]+");

    private TextLocatorSanitizer() {
    }

    /**
     * Masking variant for evidence (A9): same detectors, but PII is replaced instead of rejected.
     * Order matters: IBAN and e-mail before the digit-run rule.
     */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String t = IBAN.matcher(text).replaceAll("[iban]");
        t = EMAIL.matcher(t).replaceAll("[email]");
        t = LONG_DIGITS.matcher(t).replaceAll("[digits]");
        t = GREETING_NAME.matcher(t).replaceAll("$1$2[name]");
        return t;
    }

    /** True when {@link #redact} would change the text. */
    public static boolean containsPii(String text) {
        return text != null && !text.equals(redact(text));
    }

    public static boolean allowed(String normalized) {
        if (normalized == null) return false;
        String t = normalized.trim();
        if (t.length() < MIN_LEN || t.length() > MAX_LEN) return false;
        if (t.contains("\n")) return false;
        return !(EMAIL.matcher(t).find() || LONG_DIGITS.matcher(t).find() || IBAN.matcher(t).find()
                || MONEY.matcher(t).find() || DATE_TIME.matcher(t).find() || GREETING.matcher(t).find());
    }
}
