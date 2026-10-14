package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileProof(
        String status,
        String source,
        String attemptId
) {
    public MobileProof {
        if (status == null || status.isBlank()) {
            throw new MobileIrException("PROOF_STATUS", "proof.status is required");
        }
        if (source == null || source.isBlank()) {
            throw new MobileIrException("PROOF_SOURCE", "proof.source is required");
        }
    }
}
