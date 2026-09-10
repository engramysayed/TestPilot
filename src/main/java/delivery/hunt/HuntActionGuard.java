package delivery.hunt;

import delivery.authoring.DomCandidate;
import delivery.authoring.HtmlLocatorPresence;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Rejects planner actions whose locators are not grounded in the page map or slim HTML. */
public final class HuntActionGuard {
    private static final Set<String> GROUNDED_TYPES = Set.of(
            "click", "type", "clear", "assert_visible");

    private static final Set<String> KNOWN_HTML_STRATEGIES = Set.of(
            "id", "name", "data-test", "testid", "data-testid", "data-qa",
            "css", "cssselector", "xpath", "linktext", "link_text");

    private static final Pattern CSS_ATTR = Pattern.compile(
            "^(?:[a-zA-Z][\\w-]*)?\\[([\\w-]+)\\s*=\\s*['\"]([^'\"]+)['\"]\\]$");
    private static final Pattern XPATH_ATTR = Pattern.compile(
            "^//[a-zA-Z][\\w-]*\\[@([\\w-]+)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern XPATH_LABEL_TEXT = Pattern.compile(
            "^//label\\[normalize-space\\(\\.\\)\\s*=\\s*'([^']+)'\\]");
    private static final Pattern XPATH_NESTED_LABEL = Pattern.compile(
            "label\\[(?:normalize-space\\(\\.\\)\\s*=\\s*'([^']+)'"
                    + "|contains\\(normalize-space\\(\\.\\),\\s*'([^']+)'\\))\\]");

    private final HuntPageMap pageMap;
    private final String slimHtml;

    public HuntActionGuard(HuntPageMap pageMap, String slimHtml) {
        this.pageMap = pageMap;
        this.slimHtml = slimHtml;
    }

    public Optional<String> rejectReason(Map<String, Object> action) {
        if (action == null) {
            return Optional.empty();
        }
        String type = resolveType(action);
        type = HuntActionExecutor.normalizeType(type);
        if (!GROUNDED_TYPES.contains(type)) {
            return Optional.empty();
        }

        String rawLocator = str(action.get("locator"));
        String value = rawLocator.isBlank() ? str(action.get("locatorValue")) : rawLocator;
        if (value.isBlank()) {
            return Optional.of("missing locator");
        }

        String explicitStrategy = str(action.get("locatorStrategy")).trim().toLowerCase(Locale.ROOT);
        HuntActionNormalizer.NormalizedLocator nl =
                HuntActionNormalizer.normalizeLocator(value, explicitStrategy);
        value = nl.value();
        rawLocator = value;
        String strategy = nl.strategy().isBlank() ? guessStrategy(value) : nl.strategy();
        if (!explicitStrategy.isBlank() && nl.strategy().isBlank()) {
            strategy = explicitStrategy;
        } else if (!nl.strategy().isBlank()) {
            explicitStrategy = nl.strategy();
            strategy = nl.strategy();
        }

        if (inMap(strategy, value, rawLocator) || inHtml(explicitStrategy, strategy, value, rawLocator)) {
            return Optional.empty();
        }
        return Optional.of("locator not in page map or slim DOM: " + describeLocator(strategy, value));
    }

    private boolean inMap(String strategy, String value, String rawLocator, HuntPageMap map) {
        if (map == null || map.controls() == null) {
            return false;
        }
        for (DomCandidate c : map.controls()) {
            if (matchesControl(c, strategy, value, rawLocator)) {
                return true;
            }
        }
        return false;
    }

    private boolean inMap(String strategy, String value, String rawLocator) {
        return inMap(strategy, value, rawLocator, pageMap);
    }

    private static boolean matchesControl(DomCandidate c, String strategy, String value, String rawLocator) {
        if (!strategy.isBlank() && !value.isBlank()
                && c.strategy().equalsIgnoreCase(strategy)
                && c.value().equals(value)) {
            return true;
        }
        String key = c.key();
        if (!rawLocator.isBlank()) {
            if (rawLocator.equals(c.value()) || rawLocator.equals(key)) {
                return true;
            }
            if ("id".equalsIgnoreCase(c.strategy()) && rawLocator.equals("#" + c.value())) {
                return true;
            }
            if ("css".equalsIgnoreCase(c.strategy()) && rawLocator.equals(c.value())) {
                return true;
            }
        }
        if (!value.isBlank()) {
            if (value.equals(c.value()) || value.equals(key)) {
                return true;
            }
            if ("id".equalsIgnoreCase(c.strategy()) && value.equals("#" + c.value())) {
                return true;
            }
        }
        return false;
    }

    private boolean inHtml(String explicitStrategy, String strategy, String value, String rawLocator) {
        if (slimHtml == null || slimHtml.isBlank()) {
            return false;
        }
        String locate = !rawLocator.isBlank() ? rawLocator : value;

        String hashId = extractHashId(locate);
        if (hashId != null) {
            return HtmlLocatorPresence.present("id", hashId, slimHtml);
        }

        String idEquals = extractIdEquals(locate);
        if (idEquals != null) {
            return HtmlLocatorPresence.present("id", idEquals, slimHtml);
        }

        if (explicitStrategy.isBlank()) {
            return strictHtmlPresenceCheck(strategy, value)
                    && HtmlLocatorPresence.present(strategy, value, slimHtml);
        }

        if (!KNOWN_HTML_STRATEGIES.contains(explicitStrategy)) {
            return false;
        }

        return strictHtmlPresenceCheck(explicitStrategy, value)
                && HtmlLocatorPresence.present(explicitStrategy, value, slimHtml);
    }

    /** True only when HtmlLocatorPresence performs a real check (not its default-true branch). */
    private static boolean strictHtmlPresenceCheck(String strategy, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (strategy) {
            case "id", "name", "data-test", "testid", "data-testid", "data-qa" -> true;
            case "css", "cssselector" -> CSS_ATTR.matcher(value).matches();
            case "xpath" -> XPATH_ATTR.matcher(value).find()
                    || XPATH_LABEL_TEXT.matcher(value).find()
                    || XPATH_NESTED_LABEL.matcher(value).find();
            default -> false;
        };
    }

    private static String extractHashId(String locator) {
        if (locator == null || !locator.startsWith("#") || locator.length() < 2) {
            return null;
        }
        String id = locator.substring(1).trim();
        if (id.isEmpty() || id.contains(" ") || id.contains("[") || id.contains(".")) {
            return null;
        }
        return id;
    }

    private static String extractIdEquals(String locator) {
        if (locator == null) {
            return null;
        }
        String lower = locator.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("id=")) {
            String id = locator.substring(3).trim();
            if ((id.startsWith("'") && id.endsWith("'")) || (id.startsWith("\"") && id.endsWith("\""))) {
                id = id.substring(1, id.length() - 1);
            }
            return id.isBlank() ? null : id;
        }
        return null;
    }

    private static String guessStrategy(String locator) {
        if (locator.startsWith("//") || locator.startsWith("(//")) {
            return "xpath";
        }
        if (locator.startsWith("#")) {
            return "css";
        }
        if (locator.contains("[") || locator.contains(".")) {
            return "css";
        }
        return "css";
    }

    private static String resolveType(Map<String, Object> action) {
        String type = str(action.get("type"));
        if (type.isBlank()) {
            type = str(action.get("action"));
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private static String describeLocator(String strategy, String value) {
        if (strategy.isBlank()) {
            return value;
        }
        return strategy + ":" + value;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
