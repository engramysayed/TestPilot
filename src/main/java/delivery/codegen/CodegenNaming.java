package delivery.codegen;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Customer-aligned codegen naming: locator suffixes, Step-aligned action/assert methods.
 */
public final class CodegenNaming {
    private static final Pattern XPATH_TEXT = Pattern.compile(
            "contains\\s*\\(\\s*normalize-space\\s*\\(\\s*\\.\\s*\\)\\s*,\\s*'([^']{1,80})'\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XPATH_TEXT_DQ = Pattern.compile(
            "contains\\s*\\(\\s*normalize-space\\s*\\(\\s*\\.\\s*\\)\\s*,\\s*\"([^\"]{1,80})\"\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_PHRASE_SQ = Pattern.compile(
            "label\\s*\\[\\s*normalize-space\\s*\\(\\s*\\.\\s*\\)\\s*=\\s*'([^']{1,80})'\\s*\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_PHRASE_DQ = Pattern.compile(
            "label\\s*\\[\\s*normalize-space\\s*\\(\\s*\\.\\s*\\)\\s*=\\s*\"([^\"]{1,80})\"\\s*\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARIA_LABEL_SQ = Pattern.compile(
            "(?:\\[@)?aria-label\\s*=\\s*'([^']{1,80})'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARIA_LABEL_DQ = Pattern.compile(
            "(?:\\[@)?aria-label\\s*=\\s*\"([^\"]{1,80})\"",
            Pattern.CASE_INSENSITIVE);
    /** e.g. {@code (//div[@role='combobox'])[4]} → Combobox_4 */
    private static final Pattern ORDINAL_ROLE = Pattern.compile(
            "@role\\s*=\\s*['\"]([A-Za-z][A-Za-z0-9_-]*)['\"][^\\]]*\\]\\)\\s*\\[\\s*(\\d+)\\s*\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_IN_RATIONALE = Pattern.compile(
            "(?i)\\bfield=([^:\\s|]+)");
    private static final Set<String> BANNED_VERB_TOKENS = Set.of(
            "type", "select", "assert", "click", "element");

    private CodegenNaming() {
    }

    /** Stem for Locators/Actions class pair (no Page/Locators/Actions suffix). */
    public static String pageStem(String pageName) {
        String raw = pageName == null || pageName.isBlank() ? "Page" : pageName.trim();
        String cleaned = raw.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isEmpty()) {
            cleaned = "Page";
        }
        String stem = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        if (stem.endsWith("Page") && stem.length() > 4) {
            stem = stem.substring(0, stem.length() - 4);
        }
        if (stem.endsWith("Locators") && stem.length() > 8) {
            stem = stem.substring(0, stem.length() - 8);
        }
        if (stem.endsWith("Actions") && stem.length() > 7) {
            stem = stem.substring(0, stem.length() - 7);
        }
        return stem;
    }

    public static String locatorsClassName(String pageName) {
        return pageStem(pageName) + "_Locators";
    }

    public static String actionsClassName(String pageName) {
        return pageStem(pageName) + "_Actions";
    }

    /**
     * Locator field: semantic token + control suffix, e.g. {@code username_Txt_Locator}.
     */
    public static String locatorFieldName(ProvenStep step) {
        String token = semanticToken(step);
        String suffix = locatorSuffix(step);
        String field = toCamelSnake(token) + suffix;
        return sanitizeJavaIdentifier(field, "element_El_Locator");
    }

    /** Action method matching @Step, e.g. {@code click_Submit_Button}, {@code type_Username}. */
    public static String actionMethodName(ProvenStep step) {
        String action = step.action() == null ? "do" : step.action().trim().toLowerCase(Locale.ROOT);
        String token = toPascalSnake(semanticToken(step));
        return switch (action) {
            case "type", "select" -> action + "_" + token;
            case "click" -> "click_" + token + (token.toLowerCase(Locale.ROOT).contains("button") ? "" : "_Button");
            default -> action + "_" + token;
        };
    }

    /** Assert method matching @Step, e.g. {@code assert_Logged_In_Successfully_Is_Visible}. */
    public static String assertMethodName(ProvenStep step) {
        String type = step.assertionType() == null ? "visible" : step.assertionType().trim();
        if ("urlContains".equalsIgnoreCase(type)) {
            String exp = step.assertionExpected() == null ? "Url" : step.assertionExpected();
            return "assert_Url_Contains_" + toPascalSnake(exp);
        }
        String token = toPascalSnake(semanticToken(step));
        if ("textContains".equalsIgnoreCase(type) || "visible".equalsIgnoreCase(type)
                || "notVisible".equalsIgnoreCase(type)) {
            String verb = "notVisible".equalsIgnoreCase(type) ? "Is_Not_Visible" : "Is_Visible";
            return "assert_" + token + "_" + verb;
        }
        if ("checked".equalsIgnoreCase(type) || "selected".equalsIgnoreCase(type)) {
            return "assert_" + token + "_Is_Selected";
        }
        if ("unchecked".equalsIgnoreCase(type)) {
            return "assert_" + token + "_Is_Unchecked";
        }
        String cleaned = type.replaceAll("[^A-Za-z0-9]+", "_");
        return "assert_" + token + "_" + toPascalSnake(cleaned);
    }

    static String semanticToken(ProvenStep step) {
        if (step == null) {
            return "Element";
        }
        String assertType = step.assertionType() == null ? "" : step.assertionType();
        String expected = step.assertionExpected() == null ? "" : step.assertionExpected().trim();
        if (("textContains".equalsIgnoreCase(assertType) || "visible".equalsIgnoreCase(assertType)
                || "notVisible".equalsIgnoreCase(assertType))
                && !expected.isBlank()
                && expected.length() <= 80) {
            String fromExpected = acceptToken(expected);
            if (fromExpected != null) {
                return fromExpected;
            }
        }
        String locator = step.locatorValue() == null ? "" : step.locatorValue().trim();
        if (locator.toLowerCase(Locale.ROOT).contains("contains(normalize-space")) {
            String fromXpath = acceptToken(extractXpathPhrase(locator));
            if (fromXpath != null) {
                return fromXpath;
            }
        }
        if (!locator.isBlank() && !looksLikeXpathOrCss(locator)) {
            String fromBare = acceptToken(locator);
            if (fromBare != null) {
                return fromBare;
            }
        }
        if (!locator.isBlank()) {
            String attr = acceptToken(extractAttrValue(locator));
            if (attr != null) {
                return attr;
            }
            String label = acceptToken(extractLabelPhrase(locator));
            if (label != null) {
                return label;
            }
            String aria = acceptToken(extractAriaLabel(locator));
            if (aria != null) {
                return aria;
            }
        }
        String rationale = step.rationale() == null ? "" : step.rationale();
        String fromField = acceptToken(extractFieldFromRationale(rationale));
        if (fromField != null) {
            return fromField;
        }
        if (rationale.toLowerCase(Locale.ROOT).contains("username")
                || rationale.toLowerCase(Locale.ROOT).contains("type_user")) {
            return "Username";
        }
        if (rationale.toLowerCase(Locale.ROOT).contains("password")
                || rationale.toLowerCase(Locale.ROOT).contains("type_pass")) {
            return "Password";
        }
        if (rationale.toLowerCase(Locale.ROOT).contains("click_login")
                || rationale.toLowerCase(Locale.ROOT).contains("submit")) {
            return "Submit";
        }
        if (!locator.isBlank()) {
            String ordinalRole = acceptToken(extractOrdinalRoleToken(locator));
            if (ordinalRole != null) {
                return ordinalRole;
            }
            return controlHashFallback(locator);
        }
        return "Element";
    }

    static String extractFieldFromRationale(String rationale) {
        if (rationale == null || rationale.isBlank()) {
            return null;
        }
        Matcher m = FIELD_IN_RATIONALE.matcher(rationale);
        if (!m.find()) {
            return null;
        }
        return m.group(1).replace('_', ' ').trim();
    }

    static String extractOrdinalRoleToken(String locator) {
        if (locator == null || locator.isBlank()) {
            return null;
        }
        Matcher m = ORDINAL_ROLE.matcher(locator);
        if (!m.find()) {
            return null;
        }
        String role = m.group(1).trim();
        String index = m.group(2).trim();
        if (role.isEmpty() || index.isEmpty()) {
            return null;
        }
        String pascal = Character.toUpperCase(role.charAt(0))
                + role.substring(1).toLowerCase(Locale.ROOT);
        return pascal + "_" + index;
    }

    private static String acceptToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (BANNED_VERB_TOKENS.contains(token.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        return token;
    }

    private static String controlHashFallback(String locator) {
        return "Control" + String.format(Locale.ROOT, "%08x", locator.hashCode());
    }

    private static String locatorSuffix(ProvenStep step) {
        String action = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT);
        String strategy = step.locatorStrategy() == null ? "" : step.locatorStrategy().toLowerCase(Locale.ROOT);
        String value = step.locatorValue() == null ? "" : step.locatorValue().toLowerCase(Locale.ROOT);
        String assertType = step.assertionType() == null ? "" : step.assertionType().toLowerCase(Locale.ROOT);
        if ("type".equals(action) || value.contains("password") || value.contains("username")
                || value.contains("email") || value.contains("input")) {
            return "_Txt_Locator";
        }
        if ("click".equals(action) || value.contains("button") || value.contains("btn")
                || value.contains("submit")) {
            return "_Btn_Locator";
        }
        if (value.contains("link") || "a".equals(value) || value.contains("/a[")) {
            return "_Lnk_Locator";
        }
        if (!assertType.isBlank() || value.contains("h1") || value.contains("h2")
                || value.contains("title") || value.contains("label") || value.contains("message")) {
            return "_Lbl_Locator";
        }
        if ("id".equals(strategy) || "name".equals(strategy)) {
            return "_El_Locator";
        }
        return "_El_Locator";
    }

    private static boolean looksLikeXpathOrCss(String locator) {
        String l = locator.toLowerCase(Locale.ROOT);
        return l.startsWith("//") || l.startsWith(".//") || l.startsWith("(")
                || l.contains("[") || l.contains("*") || l.contains(">");
    }

    private static String extractXpathPhrase(String locator) {
        Matcher m = XPATH_TEXT.matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        m = XPATH_TEXT_DQ.matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractAttrValue(String locator) {
        Matcher m = Pattern.compile("(?:id|name|data-test(?:id)?|data-qa)=['\"]([^'\"]+)['\"]",
                Pattern.CASE_INSENSITIVE).matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("\\[@(?:id|name|data-test(?:id)?|data-qa)=['\"]([^'\"]+)['\"]\\]",
                Pattern.CASE_INSENSITIVE).matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractLabelPhrase(String locator) {
        Matcher m = LABEL_PHRASE_SQ.matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        m = LABEL_PHRASE_DQ.matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** aria-label is a separate extractor (after label phrase) per semanticToken order; not merged into extractAttrValue. */
    private static String extractAriaLabel(String locator) {
        Matcher m = ARIA_LABEL_SQ.matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        m = ARIA_LABEL_DQ.matcher(locator);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** wallet_Number style: lowercase first segment-ish, underscores between words. */
    static String toCamelSnake(String raw) {
        String pascal = toPascalSnake(raw);
        if (pascal.isEmpty()) {
            return "element";
        }
        // username_Txt → keep leading lower for simple tokens
        String[] parts = pascal.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (i > 0) {
                sb.append('_');
            }
            if (i == 0) {
                sb.append(parts[i].substring(0, 1).toLowerCase(Locale.ROOT));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            } else {
                sb.append(parts[i]);
            }
        }
        return sb.toString();
    }

    static String toPascalSnake(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Element";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (cleaned.isBlank()) {
            return "Element";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    static String sanitizeJavaIdentifier(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        if (cleaned.startsWith("_")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return fallback;
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "el_" + cleaned;
        }
        return cleaned;
    }
}
