package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Attribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
        Map<String, HookPick> first = new LinkedHashMap<>();
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
                first.putIfAbsent(css, new HookPick(
                        css,
                        el.tagName() + xpathAttrSelector(name, value),
                        el.tagName(),
                        name,
                        value,
                        el));
            }
        }
        List<HookPick> wrapperUnique = new ArrayList<>();
        for (HookPick pick : first.values()) {
            int inWrapper = 0;
            for (Element n : root.getAllElements()) {
                if (n.tagName().equalsIgnoreCase(pick.tag) && n.attr(pick.attr).equals(pick.value)) {
                    inWrapper++;
                }
            }
            if (inWrapper == 1) {
                wrapperUnique.add(pick);
            }
        }
        if (wrapperUnique.size() != 1) {
            return step;
        }
        HookPick pick = wrapperUnique.get(0);
        if (doc.select(pick.css).size() == 1) {
            return step.withLocator("css", pick.css);
        }
        if (pick.node == root) {
            return step;
        }
        String strategy = scopedStrategy(step);
        String scoped = scopedLocator(step, pick);
        List<Element> scopedMatches = HtmlLocatorPresence.matchingElements(strategy, scoped, doc);
        if (scopedMatches.size() == 1) {
            return step.withLocator(strategy, scoped);
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
        return "[" + attr + "=" + quoted(value) + "]";
    }

    private static String xpathAttrSelector(String attr, String value) {
        return "[@" + attr + "=" + quoted(value) + "]";
    }

    private static String quoted(String value) {
        if (value.indexOf('\'') >= 0 && value.indexOf('"') < 0) {
            return "\"" + value + "\"";
        }
        if (value.indexOf('"') >= 0) {
            return "'" + value.replace("'", "\\'") + "'";
        }
        return "'" + value + "'";
    }

    private static String scopedStrategy(ProvenStep step) {
        String strategy = step.locatorStrategy() == null ? "" : step.locatorStrategy().trim().toLowerCase(Locale.ROOT);
        if (strategy.equals("css") || strategy.equals("cssselector")) {
            return "css";
        }
        return "xpath";
    }

    private static String scopedLocator(ProvenStep step, HookPick pick) {
        String base = step.locatorValue() == null ? "" : step.locatorValue().trim();
        if ("css".equals(scopedStrategy(step))) {
            return base + " " + pick.css;
        }
        return base + "//" + pick.xpathStep;
    }

    private static final class HookPick {
        private final String css;
        private final String xpathStep;
        private final String tag;
        private final String attr;
        private final String value;
        private final Element node;

        private HookPick(String css, String xpathStep, String tag, String attr, String value, Element node) {
            this.css = css;
            this.xpathStep = xpathStep;
            this.tag = tag;
            this.attr = attr;
            this.value = value;
            this.node = node;
        }
    }
}
