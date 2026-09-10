package delivery.hunt;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites common small-model action shapes into the hunt allowlist before guard/execute.
 */
public final class HuntActionNormalizer {
    private static final Pattern STRATEGY_PREFIX = Pattern.compile(
            "^(?i)(css|cssselector|css_selector|xpath|id|name|linktext|link_text)\\s*[:=]\\s*(.+)$");
    private static final Pattern BARE_ATTR = Pattern.compile(
            "^(?i)([a-z][\\w-]*)\\s*=\\s*['\"]([^'\"]+)['\"]\\s*,?$");
    private static final Pattern ATTR_SELECTOR = Pattern.compile(
            "^\\[([\\w-]+)\\s*=\\s*['\"]([^'\"]+)['\"]\\]$");

    private HuntActionNormalizer() {
    }

    public static Map<String, Object> normalize(Map<String, Object> action) {
        if (action == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(action);
        coerceShorthand(out);
        String type = firstNonBlank(out.get("type"), out.get("action"));
        type = HuntActionExecutor.normalizeType(type);
        out.put("type", type);

        String locator = firstNonBlank(out.get("locator"), out.get("locatorValue"));
        String strategy = str(out.get("locatorStrategy")).trim().toLowerCase(Locale.ROOT);
        NormalizedLocator nl = normalizeLocator(locator, strategy);
        if (!nl.value().isBlank()) {
            out.put("locator", nl.value());
            out.put("locatorValue", nl.value());
            if (!nl.strategy().isBlank()) {
                out.put("locatorStrategy", nl.strategy());
            }
        }
        if ("wait".equals(type) && out.get("ms") == null && out.get("wait") != null) {
            out.put("ms", out.get("wait"));
        }
        if ("assert_text".equals(type)) {
            if (str(out.get("text")).isBlank() && !str(out.get("assert_text")).isBlank()) {
                out.put("text", out.get("assert_text"));
            }
            if (str(out.get("text")).isBlank() && !str(out.get("value")).isBlank()) {
                out.put("text", out.get("value"));
            }
        }
        return out;
    }

    static NormalizedLocator normalizeLocator(String rawLocator, String rawStrategy) {
        String strategy = rawStrategy == null ? "" : rawStrategy.trim().toLowerCase(Locale.ROOT);
        String value = rawLocator == null ? "" : rawLocator.trim();
        if (value.isBlank()) {
            return new NormalizedLocator(strategy, "");
        }
        value = value.replace("**", "").replaceAll(",+$", "").trim();

        Matcher prefixed = STRATEGY_PREFIX.matcher(value);
        if (prefixed.matches()) {
            strategy = prefixed.group(1).toLowerCase(Locale.ROOT);
            if (strategy.startsWith("css")) {
                strategy = "css";
            }
            value = prefixed.group(2).trim();
        }

        Matcher bare = BARE_ATTR.matcher(value);
        if (bare.matches()) {
            strategy = strategy.isBlank() ? "css" : strategy;
            value = "[" + bare.group(1) + "='" + bare.group(2) + "']";
        } else if (ATTR_SELECTOR.matcher(value).matches()) {
            strategy = strategy.isBlank() ? "css" : strategy;
        } else if (value.startsWith("#") || value.contains("[") || value.contains(".")) {
            strategy = strategy.isBlank() ? "css" : strategy;
        } else if (value.startsWith("//") || value.startsWith("(//")) {
            strategy = "xpath";
        }

        if ("cssselector".equals(strategy) || "css_selector".equals(strategy)) {
            strategy = "css";
        }
        return new NormalizedLocator(strategy, value);
    }

    private static void coerceShorthand(Map<String, Object> out) {
        String type = firstNonBlank(out.get("type"), out.get("action"));
        if (!type.isBlank()) {
            return;
        }
        if (out.containsKey("click")) {
            out.put("type", "click");
            if (str(out.get("locator")).isBlank()) {
                out.put("locator", out.get("click"));
            }
            return;
        }
        if (out.containsKey("wait")) {
            out.put("type", "wait");
            out.put("ms", out.get("wait"));
            return;
        }
        if (out.containsKey("assert_text")) {
            out.put("type", "assert_text");
            if (str(out.get("text")).isBlank()) {
                out.put("text", out.get("assert_text"));
            }
        }
    }

    private static String firstNonBlank(Object a, Object b) {
        String sa = str(a);
        if (!sa.isBlank()) {
            return sa;
        }
        return str(b);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    record NormalizedLocator(String strategy, String value) {
    }
}
