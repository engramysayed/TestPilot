package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Opaque reference to a protected saved credential (M3-01).
 * No embedded password/OTP/PIN.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProtectedCredentialRef(
        String credentialId,
        String logicalKey
) {
    public ProtectedCredentialRef {
        if (credentialId == null || credentialId.isBlank()) {
            throw new AuthContractException("CREDENTIAL_REF", "credentialId required");
        }
        credentialId = credentialId.trim();
        logicalKey = logicalKey == null ? "" : logicalKey.trim();
    }
}
