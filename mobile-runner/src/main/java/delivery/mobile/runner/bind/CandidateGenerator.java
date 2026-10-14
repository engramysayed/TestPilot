package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces hierarchy candidates for a query string / strategy against a snapshot. */
public final class CandidateGenerator {
    private CandidateGenerator() {}

    public static List<MobileSnapshotNode> pool(MobileSnapshot snap) {
        List<MobileSnapshotNode> out = new ArrayList<>();
        for (MobileSnapshotNode n : snap.nodes()) {
            if (NodeEligibility.isCandidateEligible(n, snap)) out.add(n);
        }
        return out;
    }

    public static List<MobileSnapshotNode> matchStrategy(
            MobileSnapshot snap,
            LocatorStrategy strategy,
            String value
    ) {
        List<MobileSnapshotNode> pool = pool(snap);
        List<MobileSnapshotNode> hits = new ArrayList<>();
        String wantExact = value == null ? "" : value;
        String wantNorm = MatchNormalizer.normalize(wantExact);

        for (MobileSnapshotNode n : pool) {
            switch (strategy) {
                case RESOURCE_ID -> {
                    if (n.resourceId().equals(wantExact) || resourceIdTail(n.resourceId()).equals(wantExact)) {
                        hits.add(n);
                    }
                }
                case ACCESSIBILITY_ID -> {
                    if (MatchNormalizer.equalsNormalized(n.contentDesc(), wantExact)
                            || n.contentDesc().equals(wantExact)) {
                        hits.add(n);
                    }
                }
                case TEXT -> {
                    // hint is not value: match text (normalized) OR content-desc; empty text + hint match is hint-only flag elsewhere
                    if (!n.text().isEmpty() && MatchNormalizer.equalsNormalized(n.rawText(), wantExact)) {
                        hits.add(n);
                    } else if (n.text().isEmpty() && MatchNormalizer.equalsNormalized(n.contentDesc(), wantExact)) {
                        hits.add(n);
                    }
                }
                case CLASS_NAME -> {
                    if (n.className().equals(wantExact) || n.className().endsWith("." + wantExact)) {
                        hits.add(n);
                    }
                }
                case LABEL_SIBLING -> {
                    if (isLabelSiblingMatch(snap, n, wantNorm)) hits.add(n);
                }
                case XPATH -> {
                    // Relative XPath not evaluated here; no invented matches.
                }
                case CSS -> {
                    // rejected by binder for NATIVE
                }
            }
        }

        // innermost + clickable for text-like strategies
        if (strategy == LocatorStrategy.TEXT
                || strategy == LocatorStrategy.ACCESSIBILITY_ID
                || strategy == LocatorStrategy.LABEL_SIBLING) {
            hits = new ArrayList<>(InnermostFilter.innermostThenClickable(hits, snap));
        }
        return hits;
    }

    /**
     * Hint-not-value: an empty field showing hint matches as hint-borne identity but {@code text} stays "".
     */
    public static boolean matchesHintNotValue(MobileSnapshotNode n, String query) {
        if (!n.text().isEmpty() && !n.rawText().isEmpty() && !n.password()) return false;
        return MatchNormalizer.equalsNormalized(n.hint(), query);
    }

    public static List<MobileSnapshotNode> matchHint(MobileSnapshot snap, String query) {
        List<MobileSnapshotNode> hits = new ArrayList<>();
        for (MobileSnapshotNode n : pool(snap)) {
            if (matchesHintNotValue(n, query)) hits.add(n);
        }
        return new ArrayList<>(InnermostFilter.innermostThenClickable(hits, snap));
    }

    /** Flattened form: EditText / input whose preceding sibling text equals label. */
    static boolean isLabelSiblingMatch(MobileSnapshot snap, MobileSnapshotNode n, String labelNorm) {
        if (!NodeEligibility.isEditLike(n) && !n.className().toLowerCase().contains("edit")) {
            // still allow focusable empty inputs
            if (!n.focusable()) return false;
        }
        Integer parent = n.parentIndex();
        if (parent == null) return false;
        List<Integer> kids = snap.node(parent).childIndexes();
        int pos = kids.indexOf(n.index());
        if (pos <= 0) return false;
        // look at preceding siblings for a TextView-like label
        for (int i = pos - 1; i >= 0; i--) {
            MobileSnapshotNode sib = snap.node(kids.get(i));
            String t = MatchNormalizer.normalize(sib.text().isEmpty() ? sib.contentDesc() : sib.rawText());
            if (t.equals(labelNorm)) return true;
            // stop at another edit-like control
            if (NodeEligibility.isEditLike(sib)) break;
        }
        return false;
    }

    static String resourceIdTail(String resourceId) {
        int slash = resourceId.lastIndexOf('/');
        return slash >= 0 ? resourceId.substring(slash + 1) : resourceId;
    }

    /** Same-node dedup: one candidate entry per live node (best tier wins). */
    public static List<Candidate> dedupeByNode(List<Candidate> in) {
        Map<Integer, Candidate> best = new LinkedHashMap<>();
        for (Candidate c : in) {
            Candidate prev = best.get(c.nodeIndex());
            if (prev == null || c.tier().ordinal() < prev.tier().ordinal()) {
                best.put(c.nodeIndex(), c);
            }
        }
        return new ArrayList<>(best.values());
    }
}
