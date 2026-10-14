package delivery.mobile.lease;

/**
 * Durable lease states. Device {@code busy} mirrors these while a lease row exists;
 * {@code AVAILABLE} means no active lease row.
 */
public enum LeaseState {
    RESERVED,
    ACTIVE,
    RELEASING,
    QUARANTINED
}
