package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.store.PreferredHooksStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Automate emit choices applied to proven steps before page fields are written.
 * Preferred test hooks win first (W1); then the semantic ladder
 * {@code role → label → testid → CSS} ({@link SemanticLocatorLadder}). Assert-from-element
 * and trimmed ARIA/region kinds normalize onto emit assertion types (no second IR).
 */
public final class EmitStepNormalizer {
    private EmitStepNormalizer() {
    }

    public static List<TcOutcome> applyOutcomes(List<TcOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return outcomes == null ? List.of() : outcomes;
        }
        List<TcOutcome> out = new ArrayList<>(outcomes.size());
        for (TcOutcome outcome : outcomes) {
            if (outcome == null) {
                continue;
            }
            out.add(new TcOutcome(
                    outcome.tcId(),
                    outcome.title(),
                    outcome.status(),
                    applyAll(outcome.provenSteps()),
                    outcome.failureReason(),
                    outcome.evidenceDir(),
                    outcome.needsLoginBeforeMethod(),
                    applyAll(outcome.loginSteps()),
                    applyAll(outcome.setupSteps())));
        }
        return List.copyOf(out);
    }

    public static List<ProvenStep> applyAll(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ProvenStep> out = new ArrayList<>(steps.size());
        for (ProvenStep step : steps) {
            out.add(apply(step));
        }
        return List.copyOf(out);
    }

    public static ProvenStep apply(ProvenStep step) {
        if (step == null) {
            return null;
        }
        ProvenStep next = preferLocator(normalizeAssert(step), PreferredHooksStore.current());
        if (next.dropCandidates().isEmpty()) {
            return next;
        }
        LocatorCandidate main = next.dropCandidates().get(0);
        List<LocatorCandidate> rest = next.dropCandidates().size() == 1
                ? List.of()
                : next.dropCandidates().subList(1, next.dropCandidates().size());
        Chosen drop = choose(main.strategy(), main.value(), rest, PreferredHooksStore.current());
        if (!drop.changed()) {
            return next;
        }
        List<LocatorCandidate> drops = new ArrayList<>();
        drops.add(LocatorCandidate.of(drop.strategy(), drop.value()));
        drops.addAll(drop.fallbacks());
        return next.withDropCandidates(drops);
    }

    /**
     * Map menu / OutcomeKind names onto the assertion types emit already understands.
     * {@code intent:ASSERT_VISIBLE:value} is the product's element-value kind (stored as
     * textContains by classify); emit treats that suffix as {@code value}.
     */
    static ProvenStep normalizeAssert(ProvenStep step) {
        String type = step.assertionType() == null ? "" : step.assertionType().trim();
        String rationale = step.rationale() == null ? "" : step.rationale().toLowerCase(Locale.ROOT);
        String mapped = switch (type.toLowerCase(Locale.ROOT)) {
            case "text" -> "textContains";
            case "valueequals" -> "value";
            case "textexact", "exacttext" -> "textEquals";
            case "role", "ariarole", "hasrole" -> "ariaRole";
            case "arialabel", "accessiblename", "accname" -> "ariaLabel";
            case "region", "landmark", "inregion" -> "region";
            default -> type;
        };
        String rat = rationale;
        if (("visible".equalsIgnoreCase(type) || type.isBlank())
                && (rat.contains("intent:assert_visible:aria")
                || rat.contains("intent:assert_aria")
                || rat.contains("assert:aria-role"))) {
            mapped = "ariaRole";
        }
        if (("visible".equalsIgnoreCase(type) || type.isBlank())
                && (rat.contains("intent:assert_region")
                || rat.contains("assert:region")
                || rat.contains("assert:landmark"))) {
            mapped = "region";
        }
        if (("textcontains".equalsIgnoreCase(type) || type.isBlank())
                && rationale.contains("intent:assert_visible:value")) {
            mapped = "value";
        }
        if (mapped.equals(type)) {
            return step;
        }
        return step.withAssertionType(mapped);
    }

    static ProvenStep preferLocator(ProvenStep step, List<String> hooks) {
        if (step == null) {
            return null;
        }
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            return step;
        }
        Chosen chosen = choose(step.locatorStrategy(), step.locatorValue(), step.locatorCandidates(), hooks);
        if (!chosen.changed()) {
            return step;
        }
        return step.withLocator(chosen.strategy(), chosen.value()).withLocatorCandidates(chosen.fallbacks());
    }

    private static Chosen choose(
            String strategy, String value, List<LocatorCandidate> candidates, List<String> hooks) {
        List<String> activeHooks = hooks == null ? List.of() : hooks;
        // Empty preferred hooks: W1 keeps proven primary (no candidate promotion).
        // Non-empty hooks: preferred-hook-first, then semantic ladder role→label→testid→CSS.
        boolean promote = !activeHooks.isEmpty();
        SemanticLocatorLadder.Pick pick = SemanticLocatorLadder.pick(
                strategy, value, candidates, activeHooks, promote);
        if (pick.rejected()) {
            return Chosen.unchanged(strategy, value);
        }
        List<LocatorCandidate> fallbacks = new ArrayList<>();
        for (LocatorCandidate c : pick.fallbacks()) {
            if (c == null) {
                continue;
            }
            Loc loc = Loc.plain(c.strategy(), c.value());
            if (isTextLocator(loc) || SemanticLocatorLadder.isNonUnique(c.strategy(), c.value())) {
                continue;
            }
            fallbacks.add(c);
        }
        fallbacks.sort(Comparator.comparingInt(
                (LocatorCandidate c) -> SemanticLocatorLadder.rank(c.strategy(), c.value(), activeHooks)).reversed());
        boolean samePrimary = pick.strategy().equalsIgnoreCase(strategy == null ? "" : strategy.trim())
                && pick.value().equals(value == null ? "" : value);
        boolean sameFalls = sameCandidates(candidates, fallbacks);
        if (samePrimary && sameFalls) {
            return Chosen.unchanged(strategy, value);
        }
        return new Chosen(pick.strategy(), pick.value(), List.copyOf(fallbacks), true);
    }

    private static boolean sameCandidates(List<LocatorCandidate> existing, List<LocatorCandidate> next) {
        List<LocatorCandidate> left = existing == null ? List.of() : existing;
        if (left.size() != next.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            LocatorCandidate a = left.get(i);
            LocatorCandidate b = next.get(i);
            if (a == null || b == null) {
                return false;
            }
            if (!a.strategy().equalsIgnoreCase(b.strategy()) || !a.value().equals(b.value())) {
                return false;
            }
        }
        return true;
    }

    static boolean isPreferred(Loc loc, List<String> hooks) {
        return PreferredHooksStore.matches(loc.value, hooks)
                || PreferredHooksStore.matches(loc.strategy, hooks);
    }

    /** Text xpath / {@code text=} locators are not ladder fallbacks once a preferred hook won. */
    static boolean isTextLocator(Loc loc) {
        String v = loc.value == null ? "" : loc.value.toLowerCase(Locale.ROOT);
        String s = loc.strategy == null ? "" : loc.strategy.toLowerCase(Locale.ROOT);
        if (v.startsWith("text=") || v.contains(">> text=") || v.contains(" text=\"")) {
            return true;
        }
        if ("xpath".equals(s) || v.contains("xpath=")) {
            return v.contains("normalize-space") || v.contains("text()") || v.contains("contains(.");
        }
        return false;
    }

    record Chosen(String strategy, String value, List<LocatorCandidate> fallbacks, boolean changed) {
        static Chosen unchanged(String strategy, String value) {
            return new Chosen(strategy, value, List.of(), false);
        }
    }

    record Loc(String strategy, String value) {
        static Loc plain(String strategy, String value) {
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
