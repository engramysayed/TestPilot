package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Runtime evidence attached to a completed job/attempt (M2-07).
 * Observed identities + lease fence only — must not mutate frozen admission inputs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeEvidence(
        ObservedAppIdentity app,
        ObservedDeviceIdentity device,
        ObservedToolchainIdentity toolchain,
        String leaseFence,
        String inputHash
) {
    public RuntimeEvidence {
        app = app == null ? ObservedAppIdentity.empty() : app;
        device = device == null ? ObservedDeviceIdentity.empty() : device;
        toolchain = toolchain == null ? ObservedToolchainIdentity.empty() : toolchain;
        leaseFence = leaseFence == null ? "" : leaseFence;
        inputHash = inputHash == null ? "" : inputHash;
    }

    public static RuntimeEvidence empty() {
        return new RuntimeEvidence(
                ObservedAppIdentity.empty(),
                ObservedDeviceIdentity.empty(),
                ObservedToolchainIdentity.empty(),
                "",
                "");
    }
}
