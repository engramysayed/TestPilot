package delivery.locator;

import delivery.authoring.GeneratedIdDetector;
import delivery.store.PreferredHooksStore;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single locator ranking used by bind (authoring), prove (runtime ranker) and codegen (ladder / bag).
 * <p>
 * Before this class four components ranked differently (bind/prove: test id first; codegen: role+aria first;
 * {@code LocatorPreference}: every id/name penalised), so the shipped framework often used a translated
 * label as primary and kept the stable test id as a fallback. Now every stage asks {@link #tier} and
 * {@link #rank} here, with the project's {@link LocatorMode}.
 */
public final class LocatorStabilityPolicy {

    private static final Map<LocatorMode, Map<LocatorTier, Integer>> RANKS = new EnumMap<>(LocatorMode.class);

    static {
        Map<LocatorTier, Integer> stable = new EnumMap<>(LocatorTier.class);
        stable.put(LocatorTier.PREFERRED_HOOK, 100);
        stable.put(LocatorTier.TEST_ID, 90);
        stable.put(LocatorTier.ID, 80);
        stable.put(LocatorTier.NAME, 75);
        stable.put(LocatorTier.ROLE_NAME, 65);
        stable.put(LocatorTier.LABEL, 60);
        stable.put(LocatorTier.TEXT, 50);
        stable.put(LocatorTier.CSS_ATTR, 40);
        stable.put(LocatorTier.XPATH_ATTR, 35);
        stable.put(LocatorTier.ORDINAL, 10);
        stable.put(LocatorTier.BRITTLE, 0);
        RANKS.put(LocatorMode.STABLE_FIRST, stable);

        Map<LocatorTier, Integer> user = new EnumMap<>(LocatorTier.class);
        user.put(LocatorTier.PREFERRED_HOOK, 100);
        user.put(LocatorTier.ROLE_NAME, 90);
        user.put(LocatorTier.LABEL, 85);
        user.put(LocatorTier.TEST_ID, 80);
        user.put(LocatorTier.TEXT, 70);
        user.put(LocatorTier.ID, 60);
        user.put(LocatorTier.NAME, 55);
        user.put(LocatorTier.CSS_ATTR, 40);
        user.put(LocatorTier.XPATH_ATTR, 35);
        user.put(LocatorTier.ORDINAL, 10);
        user.put(LocatorTier.BRITTLE, 0);
        RANKS.put(LocatorMode.USER_FACING_FIRST, user);
    }

    private static final Pattern TEST_ID_ATTR = Pattern.compile(
            "data-testid|data-test-id|data-test\\b|data-test\\s*=|data-qa\\b|data-qa\\s*=|data-cy\\b|data-cy\\s*=|data-axis-test-id",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE = Pattern.compile("(?:\\[@?role\\s*=)|getbyrole", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARIA_LABEL = Pattern.compile("aria-label(?:ledby)?\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABELISH = Pattern.compile(
            "\\[@?(?:aria-label|placeholder|title|alt)\\s*=|//label\\[|@for\\s*=|getbylabel|getbyplaceholder",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXTISH = Pattern.compile(
            "normalize-space|text\\(\\)|^text=|getbytext", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDINAL = Pattern.compile(
            ":nth-(?:child|of-type)|\\)\\s*\\[\\d+]\\s*$|\\]\\s*\\[\\d+]\\s*$|/\\w+\\[\\d+]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_ATTR = Pattern.compile("(?:^#([\\w-]+)$)|\\[@?id\\s*=\\s*['\"]([^'\"]+)['\"]\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_ATTR = Pattern.compile("\\[@?name\\s*=\\s*['\"]([^'\"]+)['\"]\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_TOKEN = Pattern.compile("\\.(-?[_a-zA-Z][\\w-]*)|\\[@?class\\s*[~*^$|]?=\\s*['\"]([^'\"]+)['\"]");

    private static final ThreadLocal<LocatorMode> ACTIVE = ThreadLocal.withInitial(() -> LocatorMode.STABLE_FIRST);

    private LocatorStabilityPolicy() {
    }

    // -------------------------------------------------------------- mode scope

    public static LocatorMode currentMode() {
        LocatorMode m = ACTIVE.get();
        return m == null ? LocatorMode.STABLE_FIRST : m;
    }

    public static Scope activate(LocatorMode mode) {
        LocatorMode previous = currentMode();
        ACTIVE.set(mode == null ? LocatorMode.STABLE_FIRST : mode);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final LocatorMode previous;

        private Scope(LocatorMode previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            ACTIVE.set(previous);
        }
    }

    // -------------------------------------------------------------- classification

    /** Tier with the currently active preferred hooks. */
    public static LocatorTier tier(String strategy, String value) {
        return tier(strategy, value, PreferredHooksStore.current());
    }

    public static LocatorTier tier(String strategy, String value, List<String> hooks) {
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return LocatorTier.BRITTLE;
        }
        // keel targets: "frame=… >> shadow=… >> css=…" — classify the innermost hop
        if ("keel".equals(s)) {
            String inner = v;
            int at = inner.lastIndexOf(" >> ");
            if (at >= 0) {
                inner = inner.substring(at + 4);
            }
            if (inner.startsWith("css=")) {
                return tier("css", inner.substring(4), hooks);
            }
            if (inner.startsWith("xpath=")) {
                return tier("xpath", inner.substring(6), hooks);
            }
            if (inner.startsWith("text=")) {
                return LocatorTier.TEXT;
            }
            return tier("css", inner, hooks);
        }
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/html") || lower.startsWith("//html")) {
            return LocatorTier.BRITTLE;
        }
        if (PreferredHooksStore.matches(v, hooks) || PreferredHooksStore.matches(s, hooks)) {
            return LocatorTier.PREFERRED_HOOK;
        }
        if ("data-testid".equals(s) || "data-test".equals(s) || "data-qa".equals(s) || "testid".equals(s)
                || "data-cy".equals(s)) {
            return GeneratedIdDetector.looksGenerated(v) ? LocatorTier.BRITTLE : LocatorTier.TEST_ID;
        }
        if ("id".equals(s)) {
            return GeneratedIdDetector.looksGenerated(v) ? LocatorTier.BRITTLE : LocatorTier.ID;
        }
        if ("name".equals(s)) {
            return GeneratedIdDetector.looksGeneratedName(v) ? LocatorTier.BRITTLE : LocatorTier.NAME;
        }
        if ("linktext".equals(s) || "partiallinktext".equals(s) || "text".equals(s)) {
            return LocatorTier.TEXT;
        }
        if (TEST_ID_ATTR.matcher(v).find()) {
            return LocatorTier.TEST_ID;
        }
        boolean ordinal = ORDINAL.matcher(v).find();
        if (ordinal && !lower.startsWith("(//input") && !lower.startsWith("(//select") && !lower.startsWith("(//textarea")) {
            return LocatorTier.ORDINAL;
        }
        if (hasGeneratedClass(v)) {
            return LocatorTier.BRITTLE;
        }
        Matcher id = ID_ATTR.matcher(v);
        if (id.find()) {
            String idVal = id.group(1) != null ? id.group(1) : id.group(2);
            if (GeneratedIdDetector.looksGenerated(idVal)) {
                return LocatorTier.BRITTLE;
            }
            if (!ROLE.matcher(v).find() && !ARIA_LABEL.matcher(v).find()) {
                return LocatorTier.ID;
            }
        }
        if (ROLE.matcher(v).find() && (ARIA_LABEL.matcher(v).find() || TEXTISH.matcher(v).find()
                || lower.contains("name:"))) {
            return LocatorTier.ROLE_NAME;
        }
        if (ARIA_LABEL.matcher(v).find() && isNativeRoleTag(v)) {
            return LocatorTier.ROLE_NAME; // button[aria-label=…] has an implicit role
        }
        Matcher name = NAME_ATTR.matcher(v);
        if (name.find()) {
            return GeneratedIdDetector.looksGeneratedName(name.group(1)) ? LocatorTier.BRITTLE : LocatorTier.NAME;
        }
        if (LABELISH.matcher(v).find()) {
            return LocatorTier.LABEL;
        }
        if (TEXTISH.matcher(v).find()) {
            return LocatorTier.TEXT;
        }
        if (ordinal) {
            return LocatorTier.ORDINAL; // (//input)[3]: allowed by the validator, still positional
        }
        if ("xpath".equals(s) || v.startsWith("/") || v.startsWith("(")) {
            return LocatorTier.XPATH_ATTR;
        }
        return LocatorTier.CSS_ATTR;
    }

    public static int rank(LocatorTier tier) {
        return rank(tier, currentMode());
    }

    public static int rank(LocatorTier tier, LocatorMode mode) {
        Map<LocatorTier, Integer> table = RANKS.get(mode == null ? LocatorMode.STABLE_FIRST : mode);
        Integer r = table.get(tier == null ? LocatorTier.BRITTLE : tier);
        return r == null ? 0 : r;
    }

    /** Rank (0–100) of a locator under the active mode + hooks. */
    public static int rank(String strategy, String value) {
        return rank(tier(strategy, value), currentMode());
    }

    public static int rank(String strategy, String value, List<String> hooks, LocatorMode mode) {
        return rank(tier(strategy, value, hooks), mode);
    }

    /** True when the locator relies on human-readable copy and breaks when the UI language changes. */
    public static boolean languageDependent(LocatorTier tier) {
        return tier == LocatorTier.ROLE_NAME || tier == LocatorTier.LABEL || tier == LocatorTier.TEXT;
    }

    /**
     * True when a CSS/XPath depends on a class name minted by a build tool (CSS modules, emotion,
     * styled-components, JSS, Tailwind JIT arbitrary values…). Such a selector dies on the next deploy.
     */
    public static boolean hasGeneratedClass(String selector) {
        if (selector == null || selector.isBlank()) {
            return false;
        }
        Matcher m = CLASS_TOKEN.matcher(selector);
        while (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            if (raw == null) {
                continue;
            }
            for (String cls : raw.trim().split("\\s+")) {
                if (GeneratedIdDetector.looksGeneratedClass(cls)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNativeRoleTag(String v) {
        String lower = v.toLowerCase(Locale.ROOT).trim();
        for (String tag : new String[]{"button", "a", "input", "select", "textarea", "summary"}) {
            if (lower.startsWith(tag + "[") || lower.startsWith("//" + tag + "[")) {
                return true;
            }
        }
        return false;
    }
}
