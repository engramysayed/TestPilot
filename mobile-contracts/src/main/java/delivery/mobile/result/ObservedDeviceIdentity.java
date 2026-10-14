package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Runtime-observed device identity (M2-07).
 * {@code serialFingerprint} is hashed only — never raw ADB serial.
 * {@code deviceId} is an opaque portal/lease id, not the serial.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObservedDeviceIdentity(
        String deviceId,
        String model,
        Integer apiLevel,
        String abi,
        String serialFingerprint
) {
    public ObservedDeviceIdentity {
        deviceId = deviceId == null ? "" : deviceId;
        model = model == null ? "" : model;
        abi = abi == null ? "" : abi;
        serialFingerprint = serialFingerprint == null ? "" : serialFingerprint.trim().toLowerCase();
        if (!serialFingerprint.isEmpty() && !serialFingerprint.matches("[0-9a-f]{8,64}")) {
            throw new IllegalArgumentException(
                    "serialFingerprint must be hex hash (use ObservedDeviceIdentity.ofHashed); raw serial refused");
        }
    }

    /** Preferred factory: hashes raw ADB serial; raw value never retained. */
    public static ObservedDeviceIdentity ofHashed(
            String deviceId, String model, Integer apiLevel, String abi, String rawSerial) {
        return new ObservedDeviceIdentity(
                deviceId, model, apiLevel, abi, SerialFingerprint.hash(rawSerial));
    }

    public static ObservedDeviceIdentity empty() {
        return new ObservedDeviceIdentity("", "", null, "", "");
    }
}
