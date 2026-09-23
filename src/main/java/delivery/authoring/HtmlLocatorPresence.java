package delivery.authoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort check that a locator value appears in the slim HTML snapshot.
 * Prevents the LLM from inventing attributes that are not on the page.
 */
public final class HtmlLocatorPresence {
    private static final Pattern DATA_TEST_ATTR = Pattern.compile(
            "data-test(?:id)?\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private HtmlLocatorPresence() {
    }

    public static boolean present(String strategy, String value, String slimHtml) {
        return !matchingElements(strategy, value, slimHtml).isEmpty();
    }

    /** Resolve the selector itself, including tag and case, rather than searching for an attribute substring. */
    public static java.util.List<org.jsoup.nodes.Element> matchingElements(String strategy, String value, String html) {
        if (html == null || html.isBlank() || value == null || value.isBlank()) return java.util.List.of();
        return matchingElements(strategy, value, org.jsoup.Jsoup.parse(html));
    }

    public static java.util.List<org.jsoup.nodes.Element> matchingElements(String strategy, String value, org.jsoup.nodes.Document doc) {
        if (doc == null || value == null || value.isBlank()) return java.util.List.of();
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        try {
            return switch (s) {
                case "id", "name", "data-test", "data-testid", "data-qa", "testid" -> {
                    String attr = s.equals("testid") ? "data-test" : s;
                    yield doc.getAllElements().stream().filter(el -> el.hasAttr(attr) && el.attr(attr).equals(value)).toList();
                }
                case "css", "cssselector" -> java.util.List.copyOf(doc.select(value));
                case "xpath" -> {
                    var source = doc.getAllElements().stream()
                            .filter(el -> value.equals(el.attr("data-keel-source-xpath"))).toList();
                    yield source.isEmpty() ? java.util.List.copyOf(doc.selectXpath(value)) : source;
                }
                default -> java.util.List.of();
            };
        } catch (RuntimeException invalid) {
            return java.util.List.of();
        }
    }
    /** Up to max values for repair prompts. */
    public static List<String> listDataTestValues(String slimHtml, int max) {
        Set<String> values = new LinkedHashSet<>();
        if (slimHtml == null || slimHtml.isBlank() || max <= 0) {
            return List.of();
        }
        Matcher m = DATA_TEST_ATTR.matcher(slimHtml);
        while (m.find() && values.size() < max) {
            values.add(m.group(1));
        }
        return new ArrayList<>(values);
    }

}
