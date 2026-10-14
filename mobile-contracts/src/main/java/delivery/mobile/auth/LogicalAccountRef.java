package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Logical account identity for an auth profile (M3-01). Display label must not be a secret. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogicalAccountRef(
        String accountId,
        String displayLabel
) {
    public LogicalAccountRef {
        if (accountId == null || accountId.isBlank()) {
            throw new AuthContractException("ACCOUNT_REF", "accountId required");
        }
        accountId = accountId.trim();
        displayLabel = displayLabel == null ? "" : displayLabel.trim();
    }
}
