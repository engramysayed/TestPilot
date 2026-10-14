package delivery.mobile.permission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Recorded device setting mutation for restore diagnostics (M3-05). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceSettingChange(
        String key,
        String previousValue,
        String newValue,
        boolean restored,
        String restoreDiagnostic
) {
    public DeviceSettingChange {
        key = key == null ? "" : key;
        previousValue = previousValue == null ? "" : previousValue;
        newValue = newValue == null ? "" : newValue;
        restoreDiagnostic = restoreDiagnostic == null ? "" : restoreDiagnostic;
    }
}
