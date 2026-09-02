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
    private static final Pattern CSS_ATTR = Pattern.compile(
            "^[a-zA-Z][\\w-]*\\[([\\w-]+)\\s*=\\s*['\"]([^'\"]+)['\"]\\]$");
    private static final Pattern XPATH_ATTR = Pattern.compile(
            "^//[a-zA-Z][\\w-]*\\[@([\\w-]+)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern DATA_TEST_ATTR = Pattern.compile(
            "data-test(?:id)?\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern XPATH_LABEL_TEXT = Pattern.compile(
            "^//label\\[normalize-space\\(\\.\\)\\s*=\\s*'([^']+)'\\]");
    private static final Pattern XPATH_NESTED_LABEL = Pattern.compile(
            "label\\[(?:normalize-space\\(\\.\\)\\s*=\\s*'([^']+)'"
                    + "|contains\\(normalize-space\\(\\.\\),\\s*'([^']+)'\\))\\]");

    private HtmlLocatorPresence() {
    }

    public static boolean present(String strategy, String value, String slimHtml) {
        if (slimHtml == null || slimHtml.isBlank() || value == null || value.isBlank()) {
            return true;
        }
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        String v = value.trim();
        return switch (s) {
            case "id" -> hasAttr(slimHtml, "id", v);
            case "name" -> hasAttr(slimHtml, "name", v);
            case "data-test", "testid" -> hasAttr(slimHtml, "data-test", v);
            case "data-testid" -> hasAttr(slimHtml, "data-testid", v);
            case "data-qa" -> hasAttr(slimHtml, "data-qa", v);
            case "css", "cssselector" -> {
                Matcher m = CSS_ATTR.matcher(v);
                yield m.matches() && hasAttr(slimHtml, m.group(1), m.group(2));
            }
            case "xpath" -> {
                Matcher label = XPATH_LABEL_TEXT.matcher(v);
                if (label.find()) {
                    yield labelWithControlExists(slimHtml, label.group(1));
                }
                Matcher nested = XPATH_NESTED_LABEL.matcher(v);
                if (nested.find()) {
                    String labelText = nested.group(1) != null ? nested.group(1) : nested.group(2);
                    yield labelWithControlExists(slimHtml, labelText);
                }
                Matcher m = XPATH_ATTR.matcher(v);
                yield m.find() && hasAttr(slimHtml, m.group(1), m.group(2));
            }
            default -> true;
        };
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

    /** The label has to exist and actually have a control to point at. */
    private static boolean labelWithControlExists(String html, String labelText) {
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            String want = labelText == null ? "" : labelText.trim();
            for (org.jsoup.nodes.Element label : doc.select("label")) {
                String t = label.ownText().isBlank() ? label.text() : label.ownText();
                if (!want.equalsIgnoreCase(t == null ? "" : t.trim())) {
                    continue;
                }
                if (!label.select("input, select, textarea").isEmpty()) {
                    return true;
                }
                if (label.nextElementSibling() != null || label.parent() != null) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasAttr(String html, String attr, String expected) {
        String a = attr.toLowerCase(Locale.ROOT);
        String lower = html.toLowerCase(Locale.ROOT);
        String exp = expected.toLowerCase(Locale.ROOT);
        return lower.contains(a + "=\"" + exp + "\"")
                || lower.contains(a + "='" + exp + "'");
    }
}
