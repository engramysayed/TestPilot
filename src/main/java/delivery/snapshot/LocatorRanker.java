package delivery.snapshot;

import delivery.store.PreferredHooksStore;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates locator candidates for one serializer element and ranks them Playwright-style:
 * preferred hook / test-id > role+name > label/placeholder > text > css #id > css class > position chain.
 * Every candidate keeps the element's frame/shadow prefix and is kept only if it resolves to exactly
 * one element in its root AND that element is the AI-chosen one (checked with KeelTargetResolver).
 */
public final class LocatorRanker {

    public static final double PREFERRED_HOOK = 1.00;
    public static final double TEST_ID = 0.95;
    public static final double ROLE_NAME = 0.85;
    public static final double LABEL = 0.75;
    public static final double TITLE_ALT = 0.70;
    /** Contract v2 text= tier: last resort, never main when a stable attribute candidate verified. */
    public static final double TEXT = 0.65;
    /** Highest contract version the consumer (Keel Codegen) accepts; text= needs 2. */
    public static final String MAX_VERSION_KEY = "keel.contract.max-version";
    static final Set<String> STABLE_TIERS_PREFIX = Set.of("hook:", "testid:", "role+name", "aria-label", "placeholder", "name", "title", "alt");
    public static final double CSS_ID = 0.60;
    public static final double CSS_ID_DYNAMIC = 0.45;
    public static final double CSS_CLASS = 0.40;
    public static final double POSITION = 0.20;

    private static final List<String> BUILTIN_TEST_ID_ATTRS = List.of("data-testid", "data-test", "data-qa", "data-cy");
    private static final Set<String> NATIVE_ROLE_TAGS = Set.of("button", "a", "input", "select", "textarea", "summary");

    private LocatorRanker() {
    }

    /** Site preferred hooks plus standard test-id attributes, deduped lowercase. */
    static List<String> testIdAttrs() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String hook : PreferredHooksStore.current()) {
            if (hook != null && !hook.isBlank()) {
                out.add(hook.toLowerCase(Locale.ROOT));
            }
        }
        out.addAll(BUILTIN_TEST_ID_ATTRS);
        return List.copyOf(out);
    }

    /** Unverified candidates, best first (deduplicated by target string). */
    public static List<ScoredLocator> generate(SnapshotCandidate c, List<String> preferredAttrs) {
        Map<String, ScoredLocator> out = new LinkedHashMap<>();
        String tag = c.tag();
        Map<String, String> a = c.attrs();
        for (String attr : preferredAttrs == null ? List.<String>of() : preferredAttrs)
            if (has(a, attr)) add(out, c, attrCss(tag, attr, a.get(attr)), PREFERRED_HOOK, "hook:" + attr);
        for (String attr : testIdAttrs())
            if (has(a, attr)) add(out, c, attrCss(tag, attr, a.get(attr)), TEST_ID, "testid:" + attr);
        if (has(a, "aria-label")) {
            String lbl = a.get("aria-label");
            if (has(a, "role")) add(out, c, "[role=\"" + esc(a.get("role")) + "\"][aria-label=\"" + esc(lbl) + "\"]", ROLE_NAME, "role+name");
            else if (NATIVE_ROLE_TAGS.contains(tag)) add(out, c, tag + "[aria-label=\"" + esc(lbl) + "\"]", ROLE_NAME, "role+name");
            else add(out, c, tag + "[aria-label=\"" + esc(lbl) + "\"]", LABEL, "aria-label");
        }
        if (has(a, "placeholder")) add(out, c, attrCss(tag, "placeholder", a.get("placeholder")), LABEL, "placeholder");
        if (has(a, "name") && Set.of("input", "select", "textarea", "button").contains(tag))
            add(out, c, attrCss(tag, "name", a.get("name")), LABEL, "name");
        if (has(a, "title")) add(out, c, attrCss(tag, "title", a.get("title")), TITLE_ALT, "title");
        if (has(a, "alt")) add(out, c, attrCss(tag, "alt", a.get("alt")), TITLE_ALT, "alt");
        if (has(a, "id")) add(out, c, "[id=\"" + esc(a.get("id")) + "\"]",
                looksDynamic(a.get("id")) ? CSS_ID_DYNAMIC : CSS_ID, "css-id");
        if (has(a, "class"))
            for (String cls : a.get("class").trim().split("\\s+"))
                if (!cls.isBlank() && !looksDynamic(cls)) add(out, c, tag + "." + cssIdent(cls), CSS_CLASS, "css-class");
        if (!c.css().isBlank()) add(out, c, c.css(), c.css().contains(":nth-of-type") ? POSITION : CSS_CLASS, "serializer");
        return new ArrayList<>(out.values());
    }

    /**
     * Verifies candidates live and returns the top 3 (main + 2 fallbacks). Empty when the chosen
     * element cannot be resolved. Rejected (ambiguous or pointing elsewhere) candidates are dropped.
     */
    public static java.util.Optional<RankedLocators> rank(WebDriver driver, SnapshotCandidate c,
                                                          List<String> preferredAttrs, String route) {
        WebElement reference;
        try {
            reference = KeelTargetResolver.find(driver, c.target());
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        } finally {
            KeelTargetResolver.reset(driver);
        }
        List<ScoredLocator> generated = new ArrayList<>(generate(c, preferredAttrs));
        if (maxContractVersion() >= 2 && (c.interactive() || c.navItem())) {
            textCandidate(driver, c).ifPresent(generated::add);
            generated.sort(java.util.Comparator.comparingDouble(ScoredLocator::score).reversed());
        }
        List<ScoredLocator> verified = new ArrayList<>();
        for (ScoredLocator l : generated) {
            if (verified.size() == 1 + RankedLocators.MAX_FALLBACKS) break;
            if (uniqueAndSame(driver, l.target(), reference)) verified.add(l);
        }
        if (verified.isEmpty()) return java.util.Optional.empty();
        RankedLocators ranked = RankedLocators.of(verified, route);
        if ("text".equals(ranked.main().tier())) {
            java.util.Optional<ScoredLocator> stable = verified.stream().filter(LocatorRanker::isStable).findFirst();
            if (stable.isPresent()) ranked = ranked.withProven(stable.get().value());
        }
        return java.util.Optional.of(ranked);
    }

    static boolean isStable(ScoredLocator l) {
        String t = l.tier();
        if ("css-id".equals(t)) return l.score() >= CSS_ID; // non-dynamic id only
        return STABLE_TIERS_PREFIX.stream().anyMatch(t::startsWith);
    }

    public static int maxContractVersion() {
        String v = System.getProperty(MAX_VERSION_KEY);
        if (v == null || v.isBlank()) v = utils.PropertyReader.getProperty(MAX_VERSION_KEY);
        try {
            return v == null || v.isBlank() ? 1 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(MAX_VERSION_KEY + " must be an integer: " + v);
        }
    }

    /** text= candidate scoped to the element's frame/shadow root, using the shared matcher's normalization. */
    static java.util.Optional<ScoredLocator> textCandidate(WebDriver driver, SnapshotCandidate c) {
        String literal;
        try {
            WebElement el = KeelTargetResolver.find(driver, c.target());
            literal = String.valueOf(((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    KeelTargetResolver.TEXT_MATCH_JS + "\nreturn keelTextEligible(arguments[0]) ? keelTextOf(arguments[0]) : '';", el));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        } finally {
            KeelTargetResolver.reset(driver);
        }
        if (!TextLocatorSanitizer.allowed(literal)) return java.util.Optional.empty();
        KeelTarget t = c.target().withLocator(new KeelTarget.Hop(KeelTarget.Kind.TEXT, literal));
        return java.util.Optional.of(ScoredLocator.keel(t, TEXT, "text"));
    }

    /** True when target matches exactly one element in its root and (if given) it is {@code expected}. */
    public static boolean uniqueAndSame(WebDriver driver, KeelTarget target, WebElement expected) {
        try {
            List<WebElement> m = KeelTargetResolver.findAll(driver, target);
            return m != null && m.size() == 1 && (expected == null || m.get(0).equals(expected));
        } catch (UnsupportedOperationException firefox) {
            throw firefox;
        } catch (RuntimeException e) {
            return false;
        } finally {
            KeelTargetResolver.reset(driver);
        }
    }

    private static void add(Map<String, ScoredLocator> out, SnapshotCandidate c, String terminalCss, double score, String tier) {
        List<KeelTarget.Hop> hops = new ArrayList<>(c.target().hops());
        hops.set(hops.size() - 1, new KeelTarget.Hop(KeelTarget.Kind.CSS, terminalCss));
        KeelTarget t = new KeelTarget(hops);
        out.putIfAbsent(t.format(), ScoredLocator.keel(t, score, tier));
    }

    private static boolean has(Map<String, String> a, String k) {
        String v = a.get(k);
        return v != null && !v.isBlank();
    }

    private static String attrCss(String tag, String attr, String v) {
        return tag + "[" + attr + "=\"" + esc(v) + "\"]";
    }

    static String esc(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String cssIdent(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = Character.isLetterOrDigit(ch) || ch == '-' || ch == '_';
            if (i == 0 && Character.isDigit(ch)) ok = false;
            sb.append(ok ? String.valueOf(ch) : "\\" + ch);
        }
        return sb.toString();
    }

    /** Framework-generated tokens (ember123, css-1x2ab3, mui-4521, :r1:, uuid) are poor anchors. */
    static boolean looksDynamic(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        return v.matches(".*\\d{3,}.*") || v.matches("^(css|sc|jsx|emotion)-[a-z0-9]+$")
                || v.contains(":") || v.matches(".*[0-9a-f]{8}-[0-9a-f]{4}.*");
    }
}
