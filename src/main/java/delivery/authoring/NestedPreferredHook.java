package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Attribute;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * When a bound locator resolves to a wrapper, prefer a unique inner test-hook on that subtree.
 */
public final class NestedPreferredHook {
    private NestedPreferredHook() {
    }

    public static List<ProvenStep> rewrite(List<ProvenStep> steps, String slimHtml) {
        if (steps == null || steps.isEmpty()) {
            return steps == null ? List.of() : steps;
        }
        return steps.stream().map(s -> rewrite(s, slimHtml)).toList();
    }

    public static ProvenStep rewrite(ProvenStep step, String slimHtml) {
        if (step == null || slimHtml == null || slimHtml.isBlank()) {
            return step;
        }
        String action = step.action() == null ? "" : step.action().trim();
        if (!isRewritableAction(action)) {
            return step;
        }
        Document doc = Jsoup.parse(slimHtml);
        List<Element> matches = HtmlLocatorPresence.matchingElements(
                step.locatorStrategy(), step.locatorValue(), doc);
        if (matches.size() != 1) {
            return step;
        }
        Element root = matches.get(0);
        List<String> preferred = PreferredHooksStore.current();
        Set<String> cssCandidates = new LinkedHashSet<>();
        for (Element el : root.getAllElements()) {
            for (Attribute attr : el.attributes()) {
                String name = attr.getKey();
                if (!isHookAttribute(name, preferred)) {
                    continue;
                }
                String value = attr.getValue();
                if (!GeneratedIdDetector.isTrustworthyIdentifier(value)) {
                    continue;
                }
                String css = el.tagName() + cssAttrSelector(name, value);
                if (doc.select(css).size() == 1) {
                    cssCandidates.add(css);
                }
            }
        }
        if (cssCandidates.size() == 1) {
            return step.withLocator("css", cssCandidates.iterator().next());
        }
        return step;
    }

    public static boolean isPreferredHookLocator(DomCandidate c) {
        if (c == null) {
            return false;
        }
        String strategy = c.strategy() == null ? "" : c.strategy().trim().toLowerCase(Locale.ROOT);
        if (!strategy.equals("css") && !strategy.equals("xpath") && !strategy.equals("cssselector")) {
            return false;
        }
        String value = c.value();
        if (value == null || value.isBlank()) {
            return false;
        }
        if (PreferredHooksStore.matches(value, PreferredHooksStore.current())) {
            return true;
        }
        String hay = value.toLowerCase(Locale.ROOT);
        return hay.contains("data-axis-test-id")
                || hay.contains("data-testid")
                || hay.contains("data-test=")
                || hay.contains("data-qa=");
    }

    public static Optional<DomCandidate> pickNamed(List<DomCandidate> matches) {
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }
        DomCandidate only = null;
        int count = 0;
        for (DomCandidate c : matches) {
            if (!isPreferredHookLocator(c)) {
                continue;
            }
            count++;
            only = c;
            if (count > 1) {
                return Optional.empty();
            }
        }
        return count == 1 ? Optional.of(only) : Optional.empty();
    }

    private static boolean isRewritableAction(String action) {
        return "click".equalsIgnoreCase(action)
                || "type".equalsIgnoreCase(action)
                || "clear".equalsIgnoreCase(action)
                || "select".equalsIgnoreCase(action);
    }

    private static boolean isHookAttribute(String name, List<String> preferred) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hook : preferred) {
            if (hook != null && hook.equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return DomCandidateExtractor.isTestHookAttr(name);
    }

    private static String cssAttrSelector(String attr, String value) {
        if (value.indexOf('\'') >= 0 && value.indexOf('"') < 0) {
            return "[" + attr + "=\"" + value + "\"]";
        }
        if (value.indexOf('"') >= 0) {
            return "[" + attr + "='" + value.replace("'", "\\'") + "']";
        }
        return "[" + attr + "='" + value + "']";
    }
}
