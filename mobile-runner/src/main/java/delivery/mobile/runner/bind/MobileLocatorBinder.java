package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Locator overrides + ranked candidates with uniqueness and action eligibility (M2-02).
 * <ul>
 *   <li>USER origin wins when unique and action-eligible</li>
 *   <li>Bad USER locator records fallback ({@link BindStatus#USER_LOCATOR_REJECTED} or bound via hierarchy with {@code userFallback})</li>
 *   <li>Exact selector bytes preserved on {@link Candidate#selectorValue()}</li>
 *   <li>Same-node dedup; distinct identical labels stay {@link BindStatus#AMBIGUOUS}</li>
 *   <li>CSS rejected in NATIVE; no invented Flutter children</li>
 * </ul>
 */
public final class MobileLocatorBinder {

    /**
     * @param preferHierarchyFallback when true and USER rejects, attempt hierarchy proposals for same value
     *                                and return BOUND with {@code userFallback=true}; when false, return USER_LOCATOR_REJECTED
     *                                still carrying hierarchy diagnostics in {@link BindResult#ranked()}.
     */
    public BindResult bind(MobileSnapshot snap, LocatorRequest req, boolean preferHierarchyFallback) {
        if (snap == null || !snap.usable()) {
            return BindResult.notFound(List.of(), false, "snapshot unusable");
        }
        if (req == null || req.strategy() == null) {
            return BindResult.invalid("strategy required");
        }
        if ("NATIVE".equals(req.contextKind()) && !req.strategy().nativeAllowed()) {
            return BindResult.invalid("CSS refused in NATIVE_APP");
        }
        if (req.strategy() == LocatorStrategy.CSS) {
            return BindResult.invalid("CSS refused in NATIVE_APP");
        }

        if (req.origin() == LocatorOrigin.USER) {
            return bindUser(snap, req, preferHierarchyFallback);
        }
        return bindHierarchy(snap, req, false);
    }

    public BindResult bind(MobileSnapshot snap, LocatorRequest req) {
        return bind(snap, req, true);
    }

    private BindResult bindUser(MobileSnapshot snap, LocatorRequest req, boolean preferHierarchyFallback) {
        List<Candidate> userHits = resolve(snap, req.strategy(), req.value(), LocatorOrigin.USER, req.action());
        List<Candidate> eligible = userHits.stream().filter(Candidate::actionEligible).toList();

        if (eligible.size() == 1) {
            return BindResult.bound(eligible.get(0), rankAll(userHits), false);
        }
        if (eligible.size() > 1) {
            // User locator ambiguous among distinct nodes — do not invent a winner
            List<Candidate> ranked = rankAll(userHits);
            if (preferHierarchyFallback) {
                BindResult fb = bindHierarchy(snap, req.withOrigin(LocatorOrigin.HIERARCHY), true);
                if (fb.isBound()) {
                    return BindResult.bound(fb.selected().orElseThrow(), merge(ranked, fb.ranked()), true);
                }
            }
            return BindResult.userRejected(ranked, "USER locator ambiguous (" + eligible.size() + ")");
        }

        // zero eligible — record rejection + optional hierarchy fallback
        List<Candidate> ranked = rankAll(userHits);
        String why = userHits.isEmpty() ? "USER locator not found" : "USER locator not action-eligible";
        if (preferHierarchyFallback) {
            BindResult fb = proposeHierarchy(snap, req.value(), req.action());
            if (fb.isBound()) {
                return BindResult.bound(fb.selected().orElseThrow(), merge(ranked, fb.ranked()), true);
            }
            List<Candidate> diag = merge(ranked, fb.ranked());
            return BindResult.userRejected(diag, why + "; hierarchy also " + fb.status());
        }
        return BindResult.userRejected(ranked, why);
    }

    private BindResult bindHierarchy(MobileSnapshot snap, LocatorRequest req, boolean userFallback) {
        List<Candidate> hits = resolve(snap, req.strategy(), req.value(), LocatorOrigin.HIERARCHY, req.action());
        return finish(hits, userFallback, req.action());
    }

    /** Automatic proposals across strategy tiers for a semantic value (label / id fragment). */
    public BindResult proposeHierarchy(MobileSnapshot snap, String value, ActionKind action) {
        List<Candidate> all = new ArrayList<>();
        // resource-id exact / tail
        for (MobileSnapshotNode n : CandidateGenerator.matchStrategy(snap, LocatorStrategy.RESOURCE_ID, value)) {
            all.add(Candidate.fromNode(n, LocatorStrategy.RESOURCE_ID, n.resourceId(), RankTier.RESOURCE_ID,
                    LocatorOrigin.HIERARCHY, action));
        }
        // accessibility / content-desc
        for (MobileSnapshotNode n : CandidateGenerator.matchStrategy(snap, LocatorStrategy.ACCESSIBILITY_ID, value)) {
            all.add(Candidate.fromNode(n, LocatorStrategy.ACCESSIBILITY_ID, n.contentDesc(), RankTier.ACCESSIBILITY,
                    LocatorOrigin.HIERARCHY, action));
        }
        // label-sibling
        for (MobileSnapshotNode n : CandidateGenerator.matchStrategy(snap, LocatorStrategy.LABEL_SIBLING, value)) {
            all.add(Candidate.fromNode(n, LocatorStrategy.LABEL_SIBLING, value, RankTier.LABEL_SIBLING,
                    LocatorOrigin.HIERARCHY, action));
        }
        // text
        for (MobileSnapshotNode n : CandidateGenerator.matchStrategy(snap, LocatorStrategy.TEXT, value)) {
            String sel = n.text().isEmpty() ? n.contentDesc() : n.rawText();
            all.add(Candidate.fromNode(n, LocatorStrategy.TEXT, sel, RankTier.TEXT_CLASS,
                    LocatorOrigin.HIERARCHY, action));
        }
        all = CandidateGenerator.dedupeByNode(all);
        all.sort(tierThenIndex());
        return finish(all, false, action);
    }

    private BindResult finish(List<Candidate> hits, boolean userFallback, ActionKind action) {
        List<Candidate> ranked = rankAll(hits);
        List<Candidate> eligible = ranked.stream().filter(Candidate::actionEligible).toList();
        if (eligible.isEmpty()) {
            return BindResult.notFound(ranked, userFallback, "no action-eligible candidate");
        }
        // Best tier
        RankTier best = eligible.get(0).tier();
        List<Candidate> top = eligible.stream().filter(c -> c.tier() == best).toList();
        // Distinct nodes at best tier → ambiguous (never resolve tie)
        long distinct = top.stream().map(Candidate::nodeIndex).distinct().count();
        if (distinct == 1) {
            return BindResult.bound(top.get(0), ranked, userFallback);
        }
        return BindResult.ambiguous(ranked, userFallback,
                "distinct viable controls at tier " + best + " count=" + distinct);
    }

    private List<Candidate> resolve(
            MobileSnapshot snap,
            LocatorStrategy strategy,
            String value,
            LocatorOrigin origin,
            ActionKind action
    ) {
        RankTier tier = tierFor(strategy, origin);
        List<Candidate> out = new ArrayList<>();
        for (MobileSnapshotNode n : CandidateGenerator.matchStrategy(snap, strategy, value)) {
            // Preserve exact request selector bytes when USER; otherwise observed attribute
            String sel = origin == LocatorOrigin.USER ? value : observedSelector(n, strategy, value);
            out.add(Candidate.fromNode(n, strategy, sel, tier, origin, action));
        }
        return CandidateGenerator.dedupeByNode(out);
    }

    private static String observedSelector(MobileSnapshotNode n, LocatorStrategy strategy, String value) {
        return switch (strategy) {
            case RESOURCE_ID -> n.resourceId().isEmpty() ? value : n.resourceId();
            case ACCESSIBILITY_ID -> n.contentDesc().isEmpty() ? value : n.contentDesc();
            case TEXT -> {
                if (!n.rawText().isEmpty() && !n.password()) yield n.rawText();
                yield n.contentDesc().isEmpty() ? value : n.contentDesc();
            }
            default -> value;
        };
    }

    private static RankTier tierFor(LocatorStrategy strategy, LocatorOrigin origin) {
        if (origin == LocatorOrigin.USER) return RankTier.USER_OVERRIDE;
        return switch (strategy) {
            case RESOURCE_ID -> RankTier.RESOURCE_ID;
            case ACCESSIBILITY_ID -> RankTier.ACCESSIBILITY;
            case LABEL_SIBLING -> RankTier.LABEL_SIBLING;
            case TEXT, CLASS_NAME -> RankTier.TEXT_CLASS;
            case XPATH -> RankTier.XPATH;
            default -> RankTier.OTHER;
        };
    }

    private static List<Candidate> rankAll(List<Candidate> in) {
        List<Candidate> copy = new ArrayList<>(CandidateGenerator.dedupeByNode(in));
        copy.sort(tierThenIndex());
        return List.copyOf(copy);
    }

    private static Comparator<Candidate> tierThenIndex() {
        return Comparator.comparingInt((Candidate c) -> c.tier().ordinal())
                .thenComparingInt(Candidate::nodeIndex);
    }

    private static List<Candidate> merge(List<Candidate> a, List<Candidate> b) {
        List<Candidate> all = new ArrayList<>(a);
        all.addAll(b);
        return rankAll(all);
    }
}
