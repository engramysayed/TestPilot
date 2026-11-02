package delivery.codegen;

import delivery.store.PreferredHooksStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * W3 multi-locator bag: primary + ordered fallbacks per element.
 * Honors W1 preferred-hook-first (empty hooks keep proven primary — never promote)
 * and W2 semantic ladder / uniqueness / text-drop rules.
 */
public final class MultiLocatorBag {
    /** Primary + up to {@link LocatorCandidate#MAX_FALLBACKS} - 1 fallbacks. */
    public static final int MAX_ENTRIES = LocatorCandidate.MAX_FALLBACKS;

    private MultiLocatorBag() {
    }

    public record Entry(String strategy, String value) {
        public Entry {
            strategy = strategy == null ? "" : strategy.trim();
            value = value == null ? "" : value;
        }

        public String key() {
            return strategy.toLowerCase(Locale.ROOT) + '\0' + value;
        }
    }

    public record Bag(Entry primary, List<Entry> fallbacks) {
        public Bag {
            if (primary == null) {
                primary = new Entry("", "");
            }
            fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
        }

        public boolean hasFallbacks() {
            return !fallbacks.isEmpty();
        }

        public List<Entry> all() {
            List<Entry> out = new ArrayList<>(1 + fallbacks.size());
            out.add(primary);
            out.addAll(fallbacks);
            return List.copyOf(out);
        }
    }

    /**
     * Build an ordered bag from a proven locator + candidates.
     * Empty preferred hooks: proven stays primary; candidates may only appear as fallbacks.
     * Non-empty hooks: ladder may promote preferred/role/label/testid over proven.
     */
    public static Bag build(
            String strategy,
            String value,
            List<LocatorCandidate> candidates,
            List<String> hooks
    ) {
        List<String> activeHooks = hooks == null ? PreferredHooksStore.current() : hooks;
        if (activeHooks == null) {
            activeHooks = List.of();
        }
        boolean promote = !activeHooks.isEmpty();
        SemanticLocatorLadder.Pick pick = SemanticLocatorLadder.pick(
                strategy, value, candidates, activeHooks, promote);
        Entry primary;
        List<LocatorCandidate> orderedRest;
        if (pick.rejected() || pick.strategy() == null || pick.strategy().isBlank()) {
            primary = new Entry(strategy, value);
            orderedRest = candidates == null ? List.of() : candidates;
        } else {
            primary = new Entry(pick.strategy(), pick.value());
            orderedRest = new ArrayList<>();
            if (pick.fallbacks() != null) {
                orderedRest.addAll(pick.fallbacks());
            }
            if (candidates != null) {
                orderedRest.addAll(candidates);
            }
            if (strategy != null && value != null && !value.isBlank()) {
                orderedRest.add(LocatorCandidate.of(strategy, value));
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        seen.add(primary.key());
        List<Entry> fallbacks = new ArrayList<>();
        for (LocatorCandidate c : orderedRest) {
            if (c == null || c.value() == null || c.value().isBlank()) {
                continue;
            }
            if (SemanticLocatorLadder.isNonUnique(c.strategy(), c.value())) {
                continue;
            }
            EmitStepNormalizer.Loc loc = EmitStepNormalizer.Loc.plain(c.strategy(), c.value());
            if (EmitStepNormalizer.isTextLocator(loc)) {
                continue;
            }
            Entry e = new Entry(c.strategy(), c.value());
            if (!seen.add(e.key())) {
                continue;
            }
            fallbacks.add(e);
            if (fallbacks.size() >= MAX_ENTRIES - 1) {
                break;
            }
        }
        if (promote && fallbacks.size() > 1) {
            List<String> hooksFinal = activeHooks;
            fallbacks.sort((a, b) -> Integer.compare(
                    SemanticLocatorLadder.rank(b.strategy(), b.value(), hooksFinal),
                    SemanticLocatorLadder.rank(a.strategy(), a.value(), hooksFinal)));
        }
        return new Bag(primary, List.copyOf(fallbacks));
    }

    public static Bag build(ProvenStep step) {
        if (step == null) {
            return new Bag(new Entry("", ""), List.of());
        }
        ProvenStep normalized = EmitStepNormalizer.apply(step);
        return build(
                normalized.locatorStrategy(),
                normalized.locatorValue(),
                normalized.locatorCandidates(),
                PreferredHooksStore.current());
    }
}
