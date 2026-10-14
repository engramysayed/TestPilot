package delivery.mobile.transport;

/** Transport / claim fencing codes used by M1-05 (subset aligned with contracts.md). */
public enum MobileTransportErrorCode {
    RUNNER_CAPABILITY_MISSING,
    STALE_ATTEMPT,
    STALE_DEVICE_LEASE,
    INPUT_HASH_MISMATCH,
    ARTIFACT_INVALID,
    SECRET_REQUIRED,
    SECRET_EXPIRED,
    ZIP_LIMIT,
    HMAC_LIMIT,
    CROSS_TENANT,
    MOBILE_DISABLED
}
