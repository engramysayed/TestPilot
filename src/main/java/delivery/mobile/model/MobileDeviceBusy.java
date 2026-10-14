package delivery.mobile.model;

/** Reservation / lease busy chip values (contracts + Frontend M1-07). */
public enum MobileDeviceBusy {
    AVAILABLE,
    RESERVED,
    ACTIVE,
    RELEASING,
    QUARANTINED
}
