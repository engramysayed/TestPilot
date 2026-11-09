package delivery.codegen;

import delivery.store.PreferredHooksStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Automate semantic locator ladder (W2): after preferred-hook-first (W1), prefer
 * {@code role → label → testid → CSS}. Rejects non-unique / ambiguous candidates
 * (bare role without accessible name, generic tag CSS, duplicate-looking ordinals).
 *
 * <p>Empty preferred hooks do <strong>not</strong> promote candidates over the proven
 * primary — that W1 contract stays in {@link EmitStepNormalizer} / {@link PageAccumulator#rankedFor}.
 */
public final class SemanticLocatorLadder {
    /** Higher = better. Preferred hooks sit above the semantic ladder. */
    public static final int RANK_PREFERRED_HOOK = 100;
    public static final int RANK_ROLE = 80;
    public static final int RANK_LABEL = 70;
    public static final int RANK_TESTID = 60;
    public static final int RANK_CSS = 40;
    public static final int RANK_ID = 30;
    public static final int RANK_NAME = 25;
    public static final int RANK_XPATH = 10;
    public static final int RANK_OTHER = 0;

    private static final Pattern ROLE_ATTR = Pattern.compile(
            "(?:\\[@)?role\\s*=\\s*['\"][^'\"]+['\"]|\\[role\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCESSIBLE_NAME = Pattern.compile(
            "aria-label\\s*=|\\[@?name\\s*=|\\[name\\s*=|normalize-space|text\\(\\)|"
                    + "getByLabel|getByRole\\([^\\)]*name\\s*:",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_LOCATOR = Pattern.compile(
            "aria-label\\s*=|label\\[|by\\.label|getbylabel|@for\\s*=|text\\(\\)\\s*=|"
                    + "normalize-space\\s*\\(\\s*\\.\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TESTID_LOCATOR = Pattern.compile(
            "data-testid|data-test-id|data-test\\s*=|data-qa\\s*=|data-axis-test-id|testid",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_TAG_CSS = Pattern.compile(
            "^(div|span|button|a|input|li|td|tr|p|section|main|nav|header|footer|form)"
                    + "(\\[[^\\]]+\\])?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_ROLE_CSS = Pattern.compile(
            "^([a-z]+)?\\[role\\s*=\\s*['\"]?[a-z0-9_-]+['\"]?\\]$",
            Pattern.CASE_INSENSITIVE);

    private SemanticLocatorLadder() {
    }

    public enum Kind {
        PREFERRED_HOOK, ROLE, LABEL, TESTID, CSS, ID, NAME, XPATH, OTHER
    }

    /** Result of the pick-locator playground: chosen primary or a uniqueness rejection. */
    public record Pick(
            String strategy,
            String value,
            List<LocatorCandidate> fallbacks,
            boolean rejected,
            String rejectReason) {
        public static Pick reject(String reason) {
            return new Pick("", "", List.of(), true, reason == null ? "ambiguous" : reason);
        }

        public static Pick of(String strategy, String value, List<LocatorCandidate> fallbacks) {
            return new Pick(strategy, value, fallbacks == null ? List.of() : List.copyOf(fallbacks),
                    false, "");
        }

        public boolean ok() {
            return !rejected;
        }
    }

    public static Kind kindOf(String strategy, String value, List<String> hooks) {
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        String v = value == null ? "" : value;
        if (PreferredHooksStore.matches(v, hooks) || PreferredHooksStore.matches(s, hooks)) {
            return Kind.PREFERRED_HOOK;
        }
        if (TESTID_LOCATOR.matcher(v).find()
                || "data-testid".equals(s) || "data-test".equals(s)
                || "data-qa".equals(s) || "testid".equals(s)) {
            return Kind.TESTID;
        }
        if (ROLE_ATTR.matcher(v).find() || "role".equals(s)
                || v.toLowerCase(Locale.ROOT).contains("getbyrole")) {
            return Kind.ROLE;
        }
        if (LABEL_LOCATOR.matcher(v).find() || "label".equals(s)
                || v.toLowerCase(Locale.ROOT).contains("getbylabel")) {
            return Kind.LABEL;
        }
        if ("id".equals(s)) {
            return Kind.ID;
        }
        if ("name".equals(s)) {
            return Kind.NAME;
        }
        if ("xpath".equals(s)) {
            return Kind.XPATH;
        }
        if ("css".equals(s) || "cssselector".equals(s) || "keel".equals(s)) {
            return Kind.CSS;
        }
        return Kind.OTHER;
    }

    /**
     * Rank under the shared {@link delivery.locator.LocatorStabilityPolicy} and the project's active
     * {@link delivery.locator.LocatorMode} (stable-first by default: hook → test id → id → name → role+name → …).
     * The legacy {@code RANK_*} constants and {@link #kindOf} remain for callers that only need the family.
     */
    public static int rank(String strategy, String value, List<String> hooks) {
        return delivery.locator.LocatorStabilityPolicy.rank(
                strategy, value, hooks, delivery.locator.LocatorStabilityPolicy.currentMode());
    }

    /**
     * Verified candidate that should replace the proven primary even without preferred hooks:
     * a test id (or hook) always; in stable-first mode also a human id/name when the proven primary
     * depends on visible copy (breaks when the UI language changes).
     */
    static boolean safePromotion(Loc primary, Loc candidate, List<String> hooks) {
        delivery.locator.LocatorTier pt = delivery.locator.LocatorStabilityPolicy.tier(primary.strategy, primary.value, hooks);
        delivery.locator.LocatorTier ct = delivery.locator.LocatorStabilityPolicy.tier(candidate.strategy, candidate.value, hooks);
        if (rank(candidate.strategy, candidate.value, hooks) <= rank(primary.strategy, primary.value, hooks)) {
            return false;
        }
        if (ct == delivery.locator.LocatorTier.PREFERRED_HOOK || ct == delivery.locator.LocatorTier.TEST_ID) {
            return true;
        }
        boolean stable = delivery.locator.LocatorStabilityPolicy.currentMode() == delivery.locator.LocatorMode.STABLE_FIRST;
        if (stable && (ct == delivery.locator.LocatorTier.ID || ct == delivery.locator.LocatorTier.NAME)
                && (delivery.locator.LocatorStabilityPolicy.languageDependent(pt)
                || pt == delivery.locator.LocatorTier.ORDINAL || pt == delivery.locator.LocatorTier.BRITTLE)) {
            return true;
        }
        return false;
    }

    /**
     * True when the candidate is ambiguous / non-unique and must not win the pick.
     * Bare {@code [role=…]} without an accessible name, generic tag CSS, and
     * nth-of-type ordinals are rejected.
     */
    public static boolean isNonUnique(String strategy, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        String v = value.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.contains(":nth-") || lower.matches(".*\\[\\d+]\\s*$")
                || lower.contains(")[") && lower.matches(".*\\)\\s*\\[\\d+]\\s*$")) {
            // Ordinal addressing is inherently ambiguous across reorders.
            if (ROLE_ATTR.matcher(v).find() && !ACCESSIBLE_NAME.matcher(v).find()) {
                return true;
            }
        }
        if (ROLE_ATTR.matcher(v).find() && !ACCESSIBLE_NAME.matcher(v).find()) {
            // role without name / aria-label / text anchor → first-match CSS
            if ("css".equals(s) || "cssselector".equals(s) || "keel".equals(s)
                    || BARE_ROLE_CSS.matcher(v).matches()) {
                return true;
            }
            if ("xpath".equals(s) && !lower.contains("normalize-space") && !lower.contains("text()")) {
                // //div[@role='combobox'] with no name predicate
                if (!lower.contains("@aria-label") && !lower.contains("@name")
                        && !lower.contains("[@name") && !lower.contains("contains(")) {
                    return true;
                }
            }
        }
        if (("css".equals(s) || "cssselector".equals(s)) && BARE_TAG_CSS.matcher(v).matches()) {
            // bare `div` / `button` / `div[class=…]` without test id / unique attr already classified
            if (!TESTID_LOCATOR.matcher(v).find() && !lower.contains("#") && !lower.contains("[id=")
                    && !lower.contains("[name=") && !lower.contains("[aria-label")) {
                // class-only or bare tag
                if (!lower.contains("[data-") && (lower.equals(lower.replaceAll("\\[[^]]+]", ""))
                        || lower.matches("^[a-z]+\\[class[^]]*]$"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String rejectReason(String strategy, String value) {
        if (value == null || value.isBlank()) {
            return "blank locator";
        }
        if (ROLE_ATTR.matcher(value).find() && !ACCESSIBLE_NAME.matcher(value).find()) {
            return "non-unique role without accessible name: " + value;
        }
        if (isNonUnique(strategy, value)) {
            return "ambiguous / non-unique locator: " + value;
        }
        return "rejected";
    }

    /**
     * Pick-locator playground: choose the best unique locator by preferred-hook then
     * semantic ladder; reject when the best remaining candidate is non-unique.
     *
     * @param promote when false (empty preferred hooks), keep {@code strategy}/{@code value}
     *                as primary and only filter/rank fallbacks — W1 empty-hooks contract.
     */
    public static Pick pick(
            String strategy,
            String value,
            List<LocatorCandidate> candidates,
            List<String> hooks,
            boolean promote) {
        List<Loc> locs = new ArrayList<>();
        locs.add(Loc.of(strategy, value));
        if (candidates != null) {
            for (LocatorCandidate c : candidates) {
                if (c == null || c.value() == null || c.value().isBlank()) {
                    continue;
                }
                locs.add(Loc.of(c.strategy(), c.value()));
            }
        }
        // Dedup by strategy+value
        List<Loc> dedup = new ArrayList<>();
        for (Loc loc : locs) {
            boolean seen = false;
            for (Loc d : dedup) {
                if (d.same(loc)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                dedup.add(loc);
            }
        }

        List<Loc> unique = new ArrayList<>();
        List<Loc> rejected = new ArrayList<>();
        for (Loc loc : dedup) {
            if (isNonUnique(loc.strategy, loc.value)
                    && kindOf(loc.strategy, loc.value, hooks) != Kind.PREFERRED_HOOK) {
                rejected.add(loc);
            } else {
                unique.add(loc);
            }
        }

        if (!promote) {
            // W1: empty hooks keep proven primary … except a verified test id / hook (and, stable-first,
            // a human id/name over a copy-dependent primary) — those must never sit in a fallback slot.
            Loc proven = Loc.of(strategy, value);
            Optional<Loc> better = unique.stream()
                    .filter(l -> !l.same(proven))
                    .filter(l -> safePromotion(proven, l, hooks))
                    .max(Comparator.comparingInt((Loc l) -> rank(l.strategy, l.value, hooks)));
            if (better.isPresent()) {
                Loc won = better.get();
                List<LocatorCandidate> falls = new ArrayList<>();
                unique.stream()
                        .filter(l -> !l.same(won))
                        .sorted(Comparator.comparingInt((Loc l) -> rank(l.strategy, l.value, hooks)).reversed())
                        .forEach(l -> falls.add(LocatorCandidate.of(l.strategy, l.value)));
                return Pick.of(won.strategy, won.value, falls);
            }
            Loc primary = proven;
            if (isNonUnique(primary.strategy, primary.value)
                    && kindOf(primary.strategy, primary.value, hooks) != Kind.PREFERRED_HOOK) {
                // Proven itself ambiguous — still keep it (runtime / heal owns that), but
                // do not invent a different primary here.
            }
            List<LocatorCandidate> fallbacks = new ArrayList<>();
            unique.stream()
                    .filter(l -> !l.same(primary))
                    .sorted(Comparator.comparingInt(
                            (Loc l) -> rank(l.strategy, l.value, hooks)).reversed())
                    .forEach(l -> fallbacks.add(LocatorCandidate.of(l.strategy, l.value)));
            return Pick.of(primary.strategy, primary.value, fallbacks);
        }

        Optional<Loc> best = unique.stream()
                .max(Comparator.comparingInt((Loc l) -> rank(l.strategy, l.value, hooks)));
        if (best.isEmpty()) {
            if (!rejected.isEmpty()) {
                Loc bad = rejected.get(0);
                return Pick.reject(rejectReason(bad.strategy, bad.value));
            }
            return Pick.reject("no locator candidates");
        }
        Loc won = best.get();
        // If everything unique lost to empty and only rejects remain ranked higher — already handled.
        List<LocatorCandidate> fallbacks = new ArrayList<>();
        unique.stream()
                .filter(l -> !l.same(won))
                .sorted(Comparator.comparingInt(
                        (Loc l) -> rank(l.strategy, l.value, hooks)).reversed())
                .forEach(l -> fallbacks.add(LocatorCandidate.of(l.strategy, l.value)));
        return Pick.of(won.strategy, won.value, fallbacks);
    }

    /** Convenience: promote when hooks are active. */
    public static Pick pick(
            String strategy,
            String value,
            List<LocatorCandidate> candidates,
            List<String> hooks) {
        boolean promote = hooks != null && !hooks.isEmpty();
        return pick(strategy, value, candidates, hooks, promote);
    }

    record Loc(String strategy, String value) {
        static Loc of(String strategy, String value) {
            String st = strategy == null ? "" : strategy.trim();
            String v = value == null ? "" : value.trim();
            if ("keel".equalsIgnoreCase(st) && v.regionMatches(true, 0, "css=", 0, 4)
                    && !v.contains(">>") && !v.toLowerCase(Locale.ROOT).contains("text=")) {
                return new Loc("css", v.substring(4));
            }
            return new Loc(st, v);
        }

        boolean same(Loc other) {
            return other != null
                    && strategy.equalsIgnoreCase(other.strategy)
                    && value.equals(other.value);
        }
    }
}
