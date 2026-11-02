package delivery.codegen;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repeating list/row locators become a {@code By} factory ({@code productRow(String name)})
 * instead of a single static field. The proven name stays on the action that calls the factory.
 */
final class RowLocator {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{name}");
    private static final Pattern QUOTED = Pattern.compile("'([^']{1,80})'|\"([^\"]{1,80})\"");
    /** Prefer the row label inside contains(normalize-space(.),'…') over class='…' attribute quotes. */
    private static final Pattern XPATH_TEXT = Pattern.compile(
            "contains\\s*\\(\\s*normalize-space\\s*\\(\\s*\\.\\s*\\)\\s*,\\s*'([^']{1,80})'\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XPATH_TEXT_DQ = Pattern.compile(
            "contains\\s*\\(\\s*normalize-space\\s*\\(\\s*\\.\\s*\\)\\s*,\\s*\"([^\"]{1,80})\"\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private final String key;
    private final String strategy;
    private final String literal;
    private final String expr;
    private final String factoryBase;
    /** True when the selector used {@code {name}} rather than a quoted row label. */
    private final boolean placeholder;

    private RowLocator(
            String key, String strategy, String literal, String expr, String factoryBase, boolean placeholder) {
        this.key = key;
        this.strategy = strategy;
        this.literal = literal;
        this.expr = expr;
        this.factoryBase = factoryBase;
        this.placeholder = placeholder;
    }

    String key() {
        return key;
    }

    String strategy() {
        return strategy;
    }

    String literal() {
        return literal;
    }

    /** Java expression for the selector string argument (uses parameter {@code name}). */
    String expr() {
        return expr;
    }

    String factoryBase() {
        return factoryBase;
    }

    boolean placeholder() {
        return placeholder;
    }

    static RowLocator parse(String pageKey, String strategy, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String by = byStrategy(strategy);
        if (by == null) {
            return null;
        }
        int placeholder = indexOfPlaceholder(value);
        if (placeholder >= 0) {
            if (value.indexOf("{name}", placeholder + "{name}".length()) >= 0) {
                return null;
            }
            return build(pageKey, by, value, placeholder, placeholder + "{name}".length(), "", true);
        }
        if (!looksLikeRow(value)) {
            return null;
        }
        Matcher text = XPATH_TEXT.matcher(value);
        if (!text.find()) {
            text = XPATH_TEXT_DQ.matcher(value);
            if (!text.find()) {
                text = null;
            }
        }
        if (text != null) {
            int start = text.start(1);
            int end = text.end(1);
            String literal = text.group(1);
            // reject ambiguous multi-label rows
            Matcher again = text.pattern().matcher(value);
            again.region(end, value.length());
            if (again.find()) {
                return null;
            }
            if (literal == null || literal.isBlank()) {
                return null;
            }
            return build(pageKey, by, value, start, end, literal, false);
        }
        Matcher quoted = QUOTED.matcher(value);
        if (!quoted.find()) {
            return null;
        }
        int start = quoted.start(quoted.group(1) != null ? 1 : 2);
        int end = quoted.end(quoted.group(1) != null ? 1 : 2);
        String literal = quoted.group(1) != null ? quoted.group(1) : quoted.group(2);
        if (quoted.find()) {
            return null;
        }
        if (literal.isBlank()) {
            return null;
        }
        return build(pageKey, by, value, start, end, literal, false);
    }

    private static RowLocator build(
            String pageKey, String by, String value, int start, int end, String literal, boolean placeholder) {
        String prefix = value.substring(0, start);
        String suffix = value.substring(end);
        String expr = LocatorCandidate.javaString(prefix)
                + " + name.replace(\"'\", \"\").replace(\"\\\"\", \"\") + "
                + LocatorCandidate.javaString(suffix);
        String key = by + "\0" + prefix + "\0" + suffix;
        return new RowLocator(key, by, literal == null ? "" : literal, expr, factoryBase(pageKey, value), placeholder);
    }

    private static int indexOfPlaceholder(String value) {
        Matcher m = PLACEHOLDER.matcher(value);
        return m.find() ? m.start() : -1;
    }

    static boolean looksLikeRow(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("//tr") || v.contains("/tr[") || v.contains("tbody")
                || v.contains("[role='row']") || v.contains("[role=\"row\"]")
                || v.contains("[role=row]") || v.contains("ant-table-row")
                || v.contains("role='listitem'") || v.contains("role=\"listitem\"")
                || v.contains("inventory_item") || v.contains("inventory_list")
                || v.contains("product-item") || v.contains("product_item")
                || v.contains("data-test=\"inventory-item") || v.contains("data-test='inventory-item")
                || v.contains("data-test=\"inventory-list") || v.contains("data-test='inventory-list");
    }

    static String factoryBase(String pageKey, String locator) {
        String hay = ((pageKey == null ? "" : pageKey) + " " + (locator == null ? "" : locator))
                .toLowerCase(Locale.ROOT);
        if (hay.contains("product") || hay.contains("inventory") || hay.contains("catalog")) {
            return "productRow";
        }
        return "namedRow";
    }

    /** Selenium {@code By} factory name, or null when the strategy is not a static selector. */
    private static String byStrategy(String strategy) {
        if (strategy == null) {
            return null;
        }
        return switch (strategy.trim().toLowerCase(Locale.ROOT)) {
            case "xpath" -> "xpath";
            case "css", "cssselector" -> "cssSelector";
            default -> null;
        };
    }
}
