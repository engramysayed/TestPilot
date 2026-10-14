package delivery.mobile.runner.snapshot;

/** Hard caps for secure hierarchy parsing (architecture §5). */
public record MobileSnapshotLimits(int maxBytes, int maxNodes, int maxDepth) {
    public static final MobileSnapshotLimits DEFAULT = new MobileSnapshotLimits(
            2 * 1024 * 1024, // 2 MiB page source
            50_000,
            64);

    public MobileSnapshotLimits {
        if (maxBytes < 1 || maxNodes < 1 || maxDepth < 1) {
            throw new IllegalArgumentException("limits must be positive");
        }
    }
}
