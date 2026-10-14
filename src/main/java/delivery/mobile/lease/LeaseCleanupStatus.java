package delivery.mobile.lease;

/** Cleanup acknowledgment on a device lease (contracts). */
public enum LeaseCleanupStatus {
    NONE,
    PENDING,
    ACKED,
    UNCERTAIN
}
