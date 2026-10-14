package delivery.mobile.runner.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable parsed hierarchy + metadata. Invalidate after settle/nav/scroll/keyboard/orientation. */
public final class MobileSnapshot {
    private final MobileSnapshotMeta meta;
    private final List<MobileSnapshotNode> nodes;
    private final int rootIndex;
    private final String sourceSha256;
    private final int sourceBytes;

    public MobileSnapshot(
            MobileSnapshotMeta meta,
            List<MobileSnapshotNode> nodes,
            int rootIndex,
            String sourceSha256,
            int sourceBytes
    ) {
        this.meta = Objects.requireNonNull(meta, "meta");
        this.nodes = Collections.unmodifiableList(List.copyOf(nodes));
        this.rootIndex = rootIndex;
        this.sourceSha256 = sourceSha256 == null ? "" : sourceSha256;
        this.sourceBytes = sourceBytes;
    }

    public MobileSnapshotMeta meta() { return meta; }
    public List<MobileSnapshotNode> nodes() { return nodes; }
    public int rootIndex() { return rootIndex; }
    public String sourceSha256() { return sourceSha256; }
    public int sourceBytes() { return sourceBytes; }
    public int size() { return nodes.size(); }

    public MobileSnapshotNode node(int index) {
        return nodes.get(index);
    }

    public MobileSnapshot invalidate(MobileSnapshotInvalidation reason) {
        return new MobileSnapshot(meta.withInvalidation(reason), nodes, rootIndex, sourceSha256, sourceBytes);
    }

    public boolean usable() {
        return meta.isValid();
    }
}
