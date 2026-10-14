package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileSnapshotNode;

/**
 * A ranked locator proposal bound to a live snapshot node.
 * {@link #selectorValue()} preserves exact selector bytes (Unicode); never normalized.
 */
public final class Candidate {
    private final int nodeIndex;
    private final LocatorStrategy strategy;
    private final String selectorValue;
    private final RankTier tier;
    private final LocatorOrigin origin;
    private final boolean actionEligible;
    private final String matchField;

    public Candidate(
            int nodeIndex,
            LocatorStrategy strategy,
            String selectorValue,
            RankTier tier,
            LocatorOrigin origin,
            boolean actionEligible,
            String matchField
    ) {
        this.nodeIndex = nodeIndex;
        this.strategy = strategy;
        this.selectorValue = selectorValue == null ? "" : selectorValue;
        this.tier = tier;
        this.origin = origin;
        this.actionEligible = actionEligible;
        this.matchField = matchField == null ? "" : matchField;
    }

    public int nodeIndex() { return nodeIndex; }
    public LocatorStrategy strategy() { return strategy; }
    /** Exact selector bytes as authored / observed — not matching-normalized. */
    public String selectorValue() { return selectorValue; }
    public RankTier tier() { return tier; }
    public LocatorOrigin origin() { return origin; }
    public boolean actionEligible() { return actionEligible; }
    public String matchField() { return matchField; }

    public Candidate withOrigin(LocatorOrigin o) {
        return new Candidate(nodeIndex, strategy, selectorValue, tier, o, actionEligible, matchField);
    }

    public static Candidate fromNode(
            MobileSnapshotNode node,
            LocatorStrategy strategy,
            String selectorValue,
            RankTier tier,
            LocatorOrigin origin,
            ActionKind action
    ) {
        return new Candidate(
                node.index(),
                strategy,
                selectorValue,
                tier,
                origin,
                NodeEligibility.actionEligible(node, action),
                strategy.wire());
    }
}
