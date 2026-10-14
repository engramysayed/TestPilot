package delivery.mobile.model;

/** Plain-language readiness for UI chips; Backend lock for M1-07. */
public enum MobileDeviceReadiness {
    READY,
    BUSY,
    NOT_AUTHORIZED,
    OFFLINE,
    MISSING,
    QUARANTINED,
    UNKNOWN
}
