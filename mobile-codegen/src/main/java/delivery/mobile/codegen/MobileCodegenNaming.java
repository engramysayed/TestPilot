package delivery.mobile.codegen;

import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;
import delivery.mobile.ir.MobileTarget;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mobile naming: underscore suffixes (Btn/Input/Tab/Text/…), Arabic selector preservation
 * in By expressions, Java keyword sanitization, deterministic collision hashes.
 */
public final class MobileCodegenNaming {

    static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "yield", "sealed", "permits"
    );

    private static final Pattern NON_JAVA = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final Set<String> CONTROL_SUFFIXES = Set.of(
            "Btn", "Input", "Tab", "Text", "Checkbox", "Dropdown", "Icon", "Link", "Image", "El");

    private MobileCodegenNaming() {}

    public static boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (JAVA_KEYWORDS.contains(name)) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String sanitizeJavaIdentifier(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = NON_JAVA.matcher(raw).replaceAll("_");
        cleaned = cleaned.replaceAll("_+", "_");
        if (cleaned.startsWith("_")) {
            cleaned = "N" + cleaned;
        }
        if (cleaned.isEmpty()) {
            return fallback;
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "N" + cleaned;
        }
        if (JAVA_KEYWORDS.contains(cleaned.toLowerCase(Locale.ROOT))) {
            cleaned = cleaned + "_";
        }
        if (!isValidJavaIdentifier(cleaned)) {
            return fallback;
        }
        return cleaned;
    }

    /** Screen stem from screenKey — Login → Login; blank/dump → Screen (caller demotes). */
    public static String screenStem(String screenKey) {
        if (screenKey == null || screenKey.isBlank()) {
            return "Screen";
        }
        String raw = screenKey.trim();
        if ("Screen".equalsIgnoreCase(raw) || "Page".equalsIgnoreCase(raw)) {
            return "Screen";
        }
        String cleaned = NON_JAVA.matcher(raw).replaceAll("");
        if (cleaned.isEmpty()) {
            return "Screen";
        }
        String stem = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        if (stem.endsWith("Screen") && stem.length() > 6) {
            // keep
        }
        if (JAVA_KEYWORDS.contains(stem.toLowerCase(Locale.ROOT))) {
            stem = stem + "Screen";
        }
        return stem;
    }

    public static String screenClassName(String screenKey) {
        String stem = screenStem(screenKey);
        if ("Screen".equals(stem)) {
            return "Screen";
        }
        return stem.endsWith("Screen") ? stem : stem + "Screen";
    }

    public static boolean isDumpScreenKey(String screenKey) {
        if (screenKey == null || screenKey.isBlank()) {
            return true;
        }
        String stem = screenStem(screenKey);
        return "Screen".equals(stem) || "Page".equalsIgnoreCase(stem);
    }

    public static String safeLocalVarName(String screenClass) {
        if (screenClass == null || screenClass.isBlank()) {
            return "screen";
        }
        String base = screenClass;
        if (base.endsWith("Screen") && base.length() > 6) {
            base = base.substring(0, base.length() - 6);
        }
        if (base.isEmpty()) {
            return "screen";
        }
        String name = Character.toLowerCase(base.charAt(0)) + base.substring(1);
        if (JAVA_KEYWORDS.contains(name)) {
            return name + "Screen";
        }
        return sanitizeJavaIdentifier(name, "screen");
    }

    public static String testClassName(String tcId, boolean passed) {
        String base = tcIdToClassName(tcId);
        return passed ? base : base + "Todo";
    }

    public static String tcIdToClassName(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            return "Tc";
        }
        String trimmed = tcId.trim();
        if (isValidJavaIdentifier(trimmed) && Character.isJavaIdentifierStart(trimmed.charAt(0))) {
            return trimmed;
        }
        String cleaned = trimmed.replaceAll("[^A-Za-z0-9]", "_");
        if (cleaned.isEmpty()) {
            return "Tc";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "Tc_" + cleaned;
        }
        cleaned = cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
        return sanitizeJavaIdentifier(cleaned, "Tc");
    }

    /** Identity key for naming-map pin (context + screen + strategy/value + role). */
    public static String identityKey(String screenKey, MobileStep step) {
        String ctx = step.context() == null ? "NATIVE" : String.valueOf(step.context().kind());
        MobileTarget t = step.target();
        String strat = t == null ? "" : t.strategy();
        String val = t == null ? "" : t.value();
        String role = inferRole(step);
        return ctx + "|" + nullToEmpty(screenKey) + "|" + strat + "|" + val + "|" + role;
    }

    public static String locatorFieldName(MobileStep step, String pinnedOrNull) {
        if (pinnedOrNull != null && isValidJavaIdentifier(pinnedOrNull)) {
            return pinnedOrNull;
        }
        String token = semanticToken(step);
        String suffix = controlSuffix(step);
        String field = toCamelSnake(token) + "_" + suffix + "_Locator";
        return sanitizeJavaIdentifier(field, "element_El_Locator");
    }

    public static String actionMethodName(MobileStep step, String pinnedOrNull) {
        if (pinnedOrNull != null && isValidJavaIdentifier(pinnedOrNull)) {
            return pinnedOrNull;
        }
        MobileOperation op = step.operation();
        String token = toPascalSnake(semanticToken(step));
        String name = switch (op) {
            case TAP -> "tap_" + token;
            case TYPE -> "type_" + token;
            case CLEAR -> "clear_" + token;
            case LONG_PRESS -> "longPress_" + token;
            case SCROLL_UNTIL -> "scrollUntil_" + token;
            case PRESS_BACK -> "press_Back";
            case HIDE_KEYBOARD -> "hide_Keyboard";
            case SWIPE -> "swipe_" + token;
            default -> op.name().toLowerCase(Locale.ROOT) + "_" + token;
        };
        return sanitizeJavaIdentifier(name, "do_Element");
    }

    public static String assertMethodName(MobileStep step, String pinnedOrNull) {
        if (pinnedOrNull != null && isValidJavaIdentifier(pinnedOrNull)) {
            return pinnedOrNull;
        }
        String token = toPascalSnake(semanticToken(step));
        String type = step.assertType() == null ? "visible" : step.assertType().trim();
        String name;
        if ("exactText".equalsIgnoreCase(type) || "textEquals".equalsIgnoreCase(type)) {
            name = "assert_" + token + "_Text";
        } else if ("visible".equalsIgnoreCase(type)) {
            name = "assert_" + token + "_Is_Visible";
        } else {
            name = "assert_" + token + "_" + toPascalSnake(type);
        }
        return sanitizeJavaIdentifier(name, "assert_Element_Is_Visible");
    }

    /** By expression — preserves exact Unicode/Arabic in the selector string literal. */
    public static String byExpression(MobileTarget target) {
        if (target == null) {
            return "AppiumBy.accessibilityId(\"unknown\")";
        }
        String strategy = target.strategy() == null ? "" : target.strategy().trim();
        String value = target.value() == null ? "" : target.value();
        String lit = javaStringLiteral(value);
        return switch (strategy.toLowerCase(Locale.ROOT)) {
            case "accessibilityid", "accessibility_id", "accessibility" ->
                    "AppiumBy.accessibilityId(" + lit + ")";
            case "id", "resourceid", "resource-id" ->
                    "AppiumBy.id(" + lit + ")";
            case "xpath" ->
                    "AppiumBy.xpath(" + lit + ")";
            case "classname", "class" ->
                    "AppiumBy.className(" + lit + ")";
            case "androiduiautomator", "uiautomator" ->
                    "AppiumBy.androidUIAutomator(" + lit + ")";
            default ->
                    "AppiumBy.accessibilityId(" + lit + ")";
        };
    }

    public static String javaStringLiteral(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    public static String shortIdentityHash(String identityKey) {
        int h = identityKey == null ? 0 : identityKey.hashCode();
        return String.format(Locale.ROOT, "%08x", h);
    }

    static String semanticToken(MobileStep step) {
        MobileTarget t = step.target();
        if (t != null && t.value() != null && !t.value().isBlank()) {
            String v = t.value().trim();
            // Prefer last segment of resource-id / accessibility id
            int slash = Math.max(v.lastIndexOf('/'), v.lastIndexOf(':'));
            if (slash >= 0 && slash + 1 < v.length()) {
                v = v.substring(slash + 1);
            }
            String cleaned = NON_JAVA.matcher(v).replaceAll("_");
            cleaned = cleaned.replaceAll("_+", "_");
            if (!cleaned.isBlank() && !cleaned.matches("\\d+")) {
                return cleaned;
            }
        }
        if (step.captureVar() != null && !step.captureVar().isBlank()) {
            return step.captureVar().trim();
        }
        return "Element";
    }

    static String controlSuffix(MobileStep step) {
        String token = semanticToken(step).toLowerCase(Locale.ROOT);
        MobileOperation op = step.operation();
        if (token.endsWith("_btn") || token.contains("btn") || token.contains("button")) {
            return "Btn";
        }
        if (token.contains("input") || token.contains("edit") || op == MobileOperation.TYPE) {
            return "Input";
        }
        if (token.contains("tab")) {
            return "Tab";
        }
        if (token.contains("check")) {
            return "Checkbox";
        }
        if (token.contains("dropdown") || token.contains("spinner")) {
            return "Dropdown";
        }
        if (token.contains("icon")) {
            return "Icon";
        }
        if (op == MobileOperation.ASSERT || token.contains("text") || token.contains("label")) {
            return "Text";
        }
        return "El";
    }

    static String inferRole(MobileStep step) {
        return controlSuffix(step);
    }

    static String toCamelSnake(String token) {
        String[] parts = token.split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            String p = parts[i];
            if (i == 0) {
                sb.append(Character.toLowerCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
            } else {
                sb.append('_');
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
            }
        }
        return sb.length() == 0 ? "element" : sb.toString();
    }

    static String toPascalSnake(String token) {
        String[] parts = token.split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append('_');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.length() == 0 ? "Element" : sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
