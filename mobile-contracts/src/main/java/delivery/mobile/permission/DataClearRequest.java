package delivery.mobile.permission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Explicit opt-in app data clear / reset (M3-05).
 * Never implied by FRESH_LOGIN, restart, logout, or session creation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataClearRequest(
        boolean explicitOptIn,
        String packageName,
        String reason
) {
    public DataClearRequest {
        packageName = packageName == null ? "" : packageName.trim();
        reason = reason == null ? "" : reason;
    }

    public static DataClearRequest refused() {
        return new DataClearRequest(false, "", "not requested");
    }

    public boolean mayExecute() {
        return explicitOptIn && !packageName.isBlank();
    }
}
