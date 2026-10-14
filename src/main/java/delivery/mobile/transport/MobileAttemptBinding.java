package delivery.mobile.transport;

import java.util.Objects;

/**
 * Validates claim-bound mobile fencing tuple {@code (jobId, attemptId, deviceId, leaseFence, inputHash)}.
 * Stale/mismatched values are rejected even when the runner token is valid.
 */
public final class MobileAttemptBinding {
    private final String jobId;
    private final String attemptId;
    private final String deviceId;
    private final long leaseFence;
    private final String inputHash;

    public MobileAttemptBinding(String jobId, String attemptId, String deviceId, long leaseFence, String inputHash) {
        this.jobId = requireNonBlank(jobId, "jobId");
        this.attemptId = requireNonBlank(attemptId, "attemptId");
        this.deviceId = requireNonBlank(deviceId, "deviceId");
        this.leaseFence = leaseFence;
        this.inputHash = requireNonBlank(inputHash, "inputHash");
    }

    public String jobId() { return jobId; }
    public String attemptId() { return attemptId; }
    public String deviceId() { return deviceId; }
    public long leaseFence() { return leaseFence; }
    public String inputHash() { return inputHash; }

    public void assertMatches(String jobId, String attemptId, String deviceId, Long leaseFence, String inputHash) {
        if (!this.jobId.equals(jobId)) {
            throw new MobileTransportException(MobileTransportErrorCode.CROSS_TENANT, "job mismatch");
        }
        if (!this.attemptId.equals(attemptId)) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_ATTEMPT, "stale attempt token");
        }
        if (!this.deviceId.equals(deviceId)) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_DEVICE_LEASE, "device token mismatch");
        }
        if (leaseFence == null || this.leaseFence != leaseFence) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_DEVICE_LEASE, "stale device lease fence");
        }
        if (!this.inputHash.equals(inputHash)) {
            throw new MobileTransportException(MobileTransportErrorCode.INPUT_HASH_MISMATCH, "input hash mismatch");
        }
    }

    public static MobileAttemptBinding fromManifest(MobileInputManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        return new MobileAttemptBinding(manifest.jobId(), manifest.attemptId(), manifest.deviceId(),
                manifest.leaseFence(), manifest.hash());
    }

    private static String requireNonBlank(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_ATTEMPT, name + " required");
        }
        return v;
    }
}
