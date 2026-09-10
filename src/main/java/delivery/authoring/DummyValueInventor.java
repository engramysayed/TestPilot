package delivery.authoring;

import net.datafaker.Faker;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Site-agnostic values for form controls when Excel does not supply data.
 * Uses Datafaker for realistic names/addresses; phone digit length follows maxlength
 * or a country name found in the field hint (generic country vocabulary, not a site).
 */
public final class DummyValueInventor {
    /** Seeded so a single conversion run stays stable if the same invent path is re-hit. */
    private static final Faker FAKER = new Faker(Locale.ENGLISH, new Random(42_4242L));

    private DummyValueInventor() {
    }

    public static String invent(String tag, String inputType, String name, String label, String placeholder) {
        return invent(tag, inputType, name, label, placeholder, "", "");
    }

    /**
     * @param maxLengthAttr HTML maxlength when present (digit count for phones/zips)
     * @param countryHint   optional country text from nearby UI (e.g. a country field value)
     */
    public static String invent(
            String tag,
            String inputType,
            String name,
            String label,
            String placeholder,
            String maxLengthAttr,
            String countryHint
    ) {
        String hint = join(name, label, placeholder, countryHint).toLowerCase(Locale.ROOT);
        String type = inputType == null ? "" : inputType.toLowerCase(Locale.ROOT);
        String t = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        int maxLen = parseMaxLength(maxLengthAttr);

        // OTP/MFA (often type=password for masking) must never become login secrets.
        if (looksLikeOtpHint(hint)) {
            return digits(maxLen > 0 ? Math.min(maxLen, 8) : 6);
        }

        if ("password".equals(type) || hint.contains("password") || hint.contains("passwd")) {
            return "${TARGET_PASSWORD}";
        }
        if (isLoginIdentifierHint(hint, "")) {
            return "${TARGET_USERNAME}";
        }
        if ("email".equals(type) || hint.contains("email") || hint.contains("e-mail")) {
            String local = FAKER.internet().username().replaceAll("[^a-zA-Z0-9._-]", "");
            if (local.isBlank()) {
                local = "user" + FAKER.number().digits(4);
            }
            return local + "@example.com";
        }
        if ("tel".equals(type) || hint.contains("phone") || hint.contains("mobile") || hint.contains("tel")) {
            return phoneDigits(hint, maxLen);
        }
        if ("number".equals(type) || "range".equals(type)) {
            if (hint.contains("zip") || hint.contains("postal") || hint.contains("postcode")) {
                return digits(maxLen > 0 ? maxLen : 5);
            }
            if (hint.contains("qty") || hint.contains("quantity") || hint.contains("count")) {
                return String.valueOf(1 + ThreadLocalRandom.current().nextInt(5));
            }
            return String.valueOf(FAKER.number().numberBetween(1, 99));
        }
        if ("date".equals(type)) {
            return FAKER.timeAndDate().birthday(18, 65).toString();
        }
        if ("url".equals(type) || hint.contains("website") || hint.contains("url")) {
            return "https://" + FAKER.internet().domainName();
        }
        if (hint.contains("zip") || hint.contains("postal") || hint.contains("postcode") || hint.contains("post_code")) {
            return digits(maxLen > 0 ? maxLen : 5);
        }
        if ((hint.contains("first") && hint.contains("name")) || hint.contains("firstname") || hint.contains("given")) {
            return FAKER.name().firstName();
        }
        if ((hint.contains("last") && hint.contains("name")) || hint.contains("lastname") || hint.contains("surname")
                || hint.contains("family")) {
            return FAKER.name().lastName();
        }
        if ((hint.contains("full") && hint.contains("name")) || hint.equals("name") || hint.endsWith(" name")
                || hint.contains("cardholder") || hint.contains("holder")) {
            return FAKER.name().fullName();
        }
        if (hint.contains("city") || hint.contains("town")) {
            return FAKER.address().city();
        }
        if (hint.contains("state") || hint.contains("province") || hint.contains("region")) {
            return FAKER.address().stateAbbr();
        }
        if (hint.contains("country")) {
            return FAKER.address().countryCode();
        }
        if (hint.contains("address") || hint.contains("street") || hint.contains("line1") || hint.contains("line_1")) {
            return FAKER.address().streetAddress();
        }
        if (hint.contains("company") || hint.contains("organization") || hint.contains("org")) {
            return FAKER.company().name();
        }
        if (hint.contains("card") && (hint.contains("number") || hint.contains("pan"))) {
            return "4111111111111111";
        }
        if (hint.contains("cvv") || hint.contains("cvc") || hint.contains("security code")) {
            return digits(3);
        }
        if (hint.contains("expir")) {
            int month = FAKER.number().numberBetween(1, 12);
            int year = FAKER.number().numberBetween(28, 35);
            return String.format("%02d/%02d", month, year);
        }
        if ("textarea".equals(t) || hint.contains("comment") || hint.contains("note") || hint.contains("message")
                || hint.contains("description")) {
            return FAKER.lorem().sentence(6);
        }
        if ("select".equals(t)) {
            return ""; // caller should use first real option when empty
        }
        return FAKER.lorem().word() + FAKER.number().digits(3);
    }

    /**
     * Prefer TestData column, then an explicit value in the step.
     * Does <em>not</em> invent faker values — leave blank or use {@code ${TARGET_*}} for login fields.
     * Heal / required-field fillers still call {@link #invent} directly when filling incidental DOM.
     * "Test" / "User" / "fname" / {@code <PLACEHOLDER>} are unspecified, not typed data.
     */
    public static String fromStepOrInvent(String stepText, String tag, String inputType,
                                          String name, String label, String placeholder) {
        return fromStepOrInvent(stepText, "", tag, inputType, name, label, placeholder);
    }

    public static String fromStepOrInvent(String stepText, String columnValue, String tag, String inputType,
                                          String name, String label, String placeholder) {
        String extracted = extractExplicitValue(stepText);
        String fromColumn = concreteOrNull(columnValue == null ? "" : columnValue.trim());
        String lower = stepText == null ? "" : stepText.toLowerCase(Locale.ROOT);
        boolean selectLike = lower.contains("select ") || lower.contains("choose ") || lower.contains("pick ");
        // Dropdown picks embedded in the step win over a misaligned TestData line (e.g. email on Year).
        if (selectLike && extracted != null && !extracted.isBlank()) {
            return extracted;
        }
        // Customer TestData wins for Enter/type fields.
        if (fromColumn != null) {
            return fromColumn;
        }
        if (extracted != null && !extracted.isBlank()
                && !extracted.equalsIgnoreCase(nullToEmpty(name))
                && !extracted.equalsIgnoreCase(nullToEmpty(label))) {
            return extracted;
        }
        // Login secrets come from the job credential profile — never invent passwords/usernames.
        String hint = join(name, label, placeholder, inputType, stepText).toLowerCase(Locale.ROOT);
        String type = inputType == null ? "" : inputType.toLowerCase(Locale.ROOT);
        if (looksLikeOtpHint(hint)) {
            return "";
        }
        if ("password".equals(type) || hint.contains("password") || hint.contains("passwd")) {
            return "${TARGET_PASSWORD}";
        }
        if (isLoginIdentifierHint(hint, stepText)) {
            return "${TARGET_USERNAME}";
        }
        return "";
    }

    static boolean isLoginIdentifierHint(String hint, String stepText) {
        String h = hint == null ? "" : hint.toLowerCase(Locale.ROOT).trim();
        String step = stepText == null ? "" : stepText.toLowerCase(Locale.ROOT);
        String blob = h + " " + step;
        if (blob.contains("username") || blob.contains("user name") || blob.contains("user-name")
                || blob.contains("email or phone") || blob.contains("email/phone")
                || blob.contains("email / mobile") || blob.contains("email/mobile")) {
            return true;
        }
        return "user".equals(h);
    }

    static String extractExplicitValue(String stepText) {
        if (stepText == null || stepText.isBlank()) {
            return null;
        }
        var quoted = java.util.regex.Pattern.compile("[\"']([^\"']{1,80})[\"']").matcher(stepText);
        if (quoted.find()) {
            String v = concreteOrNull(quoted.group(1).trim());
            if (v != null) {
                return v;
            }
        }
        var selectFrom = java.util.regex.Pattern.compile(
                "(?i)\\b(?:select|choose|pick)\\s+(.+?)\\s+from\\b").matcher(stepText);
        if (selectFrom.find()) {
            String v = concreteOrNull(selectFrom.group(1).trim().replaceAll("[.,;:]+$", ""));
            if (v != null) {
                return v;
            }
        }
        var valueBeforeField = java.util.regex.Pattern.compile(
                "(?i)\\b(?:enter|type|fill|input)\\s+(.+?)\\s+(?:in|into)\\s+(?:the\\s+)?\\S")
                .matcher(stepText);
        if (valueBeforeField.find()) {
            String v = concreteOrNull(valueBeforeField.group(1).trim().replaceAll("[.,;:]+$", ""));
            if (v != null) {
                return v;
            }
        }
        String lower = stepText.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(enter|type|fill|input|select)\\b.+")) {
            String[] parts = stepText.trim().split("\\s+");
            if (parts.length >= 2) {
                String last = parts[parts.length - 1].replaceAll("[.,;:]+$", "");
                String lastLower = last.toLowerCase(Locale.ROOT);
                if (last.length() >= 2
                        && !FIELD_WORDS.contains(lastLower)
                        && !lastLower.equals("field")
                        && !lastLower.equals("box")
                        && !lastLower.equals("input")) {
                    if (!looksLikeTrailingFieldNoun(parts)) {
                        String v = concreteOrNull(last);
                        if (v != null) {
                            return v;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** OTP / MFA / verification-code fields — never treat as login password. */
    public static boolean looksLikeOtpHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        String h = hint.toLowerCase(Locale.ROOT);
        return h.contains("otp")
                || h.contains("one-time")
                || h.contains("onetime")
                || h.contains("one time")
                || h.contains("mfa")
                || h.contains("2fa")
                || h.contains("totp")
                || h.contains("verification code")
                || h.contains("verify code")
                || h.contains("auth code")
                || h.contains("authentication code");
    }

    /**
     * Tokens that name the field or are Excel placeholders, not typed data.
     * "fname" / "Test" / "User" must invent; "Alice" stays literal.
     */
    static boolean looksLikeUnspecifiedValue(String extracted) {
        if (extracted == null || extracted.isBlank()) {
            return true;
        }
        String trimmed = extracted.trim();
        if (trimmed.matches("^<[^<>]+>$")) {
            return true;
        }
        String v = trimmed.toLowerCase(Locale.ROOT);
        if (FIELD_WORDS.contains(v) || PLACEHOLDER_TOKENS.contains(v)) {
            return true;
        }
        if (v.matches("^(a|an|the)\\s+.+$")) {
            return true;
        }
        return v.matches("^(first|last|full|given|family)\\s+names?$")
                || v.matches("^(e-?mail)(\\s+address)?$")
                || v.matches("^[a-z]{1,8}name$");
    }

    private static String concreteOrNull(String v) {
        if (v == null || v.isBlank() || looksLikeUnspecifiedValue(v)) {
            return null;
        }
        return v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean looksLikeTrailingFieldNoun(String[] parts) {
        if (parts.length < 2) {
            return true;
        }
        String last = parts[parts.length - 1].toLowerCase(Locale.ROOT).replaceAll("[.,;:]+$", "");
        return FIELD_WORDS.contains(last);
    }

    private static String phoneDigits(String hint, int maxLen) {
        int len = maxLen > 0 ? maxLen : countryPhoneDigits(hint);
        return digits(len);
    }

    /**
     * Common national mobile lengths when the field hint names a country. Generic vocabulary —
     * not tied to any product or host.
     */
    private static int countryPhoneDigits(String hint) {
        if (hint == null || hint.isBlank()) {
            return 10;
        }
        if (containsAny(hint, "egypt", "egyptian", "eg ")) {
            return 11;
        }
        if (containsAny(hint, "saudi", "ksa")) {
            return 9;
        }
        if (containsAny(hint, "emirates", "uae", "dubai", "abu dhabi")) {
            return 9;
        }
        if (containsAny(hint, "united kingdom", "britain", " uk", "uk ", "england")) {
            return 11;
        }
        if (containsAny(hint, "united states", "usa", " u.s", "america")) {
            return 10;
        }
        if (containsAny(hint, "india", " indian")) {
            return 10;
        }
        if (containsAny(hint, "germany", "deutschland")) {
            return 11;
        }
        if (containsAny(hint, "france", "french")) {
            return 10;
        }
        return 10;
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static int parseMaxLength(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 && n <= 32 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String digits(int len) {
        int n = Math.max(1, Math.min(len, 32));
        return FAKER.number().digits(n);
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static final java.util.Set<String> FIELD_WORDS = java.util.Set.of(
            "name", "firstname", "lastname", "username", "password", "email", "phone",
            "address", "city", "state", "zip", "postal", "code", "country", "company",
            "card", "number", "cvv", "cvc", "date", "message", "comment", "note",
            "quantity", "qty", "dropdown", "select", "option", "radio", "checkbox",
            "the", "a", "an", "into", "in", "to", "for", "your", "my",
            "fname", "lname", "uname");

    private static final java.util.Set<String> PLACEHOLDER_TOKENS = java.util.Set.of(
            "fname", "lname", "uname", "userid", "testdata", "test-data", "dummy",
            "sample", "placeholder", "foo", "bar", "baz", "xxx", "n/a", "na",
            "null", "none", "value", "data", "string", "text", "test", "user");
}
