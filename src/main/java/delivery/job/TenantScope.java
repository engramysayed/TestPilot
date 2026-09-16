package delivery.job;

/** Hosted portal jobs fail closed; CLI/tests must opt into legacy directories. */
public enum TenantScope {
    HOSTED,
    LEGACY_EXPLICIT
}
