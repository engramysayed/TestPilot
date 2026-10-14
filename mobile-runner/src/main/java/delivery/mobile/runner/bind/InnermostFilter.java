package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keep only innermost matching nodes (a match that geometrically contains another match is dropped),
 * then prefer clickable among equals (arch §5.3 / Maestro deepestMatchingElement + clickableFirst).
 * Never collapses two distinct sibling controls into one.
 */
public final class InnermostFilter {
    private InnermostFilter() {}

    public static List<MobileSnapshotNode> innermostThenClickable(
            List<MobileSnapshotNode> matches,
            MobileSnapshot snap
    ) {
        if (matches == null || matches.isEmpty()) return List.of();
        if (matches.size() == 1) return List.copyOf(matches);

        Set<Integer> dropped = new HashSet<>();
        for (MobileSnapshotNode a : matches) {
            for (MobileSnapshotNode b : matches) {
                if (a.index() == b.index()) continue;
                if (contains(a, b) && !contains(b, a)) {
                    dropped.add(a.index()); // a is outer
                } else if (isAncestor(snap, a.index(), b.index())) {
                    dropped.add(a.index());
                }
            }
        }
        List<MobileSnapshotNode> inner = new ArrayList<>();
        for (MobileSnapshotNode n : matches) {
            if (!dropped.contains(n.index())) inner.add(n);
        }
        if (inner.isEmpty()) inner = new ArrayList<>(matches);

        // clickable-first among remaining equals (same label) — only reorder, do not drop distinct
        inner.sort((x, y) -> Boolean.compare(y.clickable(), x.clickable()));
        return List.copyOf(inner);
    }

    private static boolean contains(MobileSnapshotNode outer, MobileSnapshotNode inner) {
        MobileBounds a = outer.bounds();
        MobileBounds b = inner.bounds();
        if (a == null || b == null) return false;
        return a.left() <= b.left() && a.top() <= b.top()
                && a.right() >= b.right() && a.bottom() >= b.bottom()
                && (a.width() > b.width() || a.height() > b.height());
    }

    private static boolean isAncestor(MobileSnapshot snap, int ancestorIdx, int descendantIdx) {
        Integer p = snap.node(descendantIdx).parentIndex();
        while (p != null) {
            if (p == ancestorIdx) return true;
            p = snap.node(p).parentIndex();
        }
        return false;
    }
}
