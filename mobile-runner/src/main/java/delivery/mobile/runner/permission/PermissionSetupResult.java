package delivery.mobile.runner.permission;

import delivery.mobile.permission.DataClearRequest;
import delivery.mobile.permission.DeviceSettingChange;
import delivery.mobile.permission.PermissionPlan;
import delivery.mobile.result.SetupOutcome;

import java.util.List;

public record PermissionSetupResult(
        boolean ok,
        String failureCode,
        SetupOutcome setupOutcome,
        PermissionPlan plan,
        boolean dataCleared,
        List<DeviceSettingChange> settingRestores,
        String detail
) {
    public PermissionSetupResult {
        failureCode = failureCode == null ? "" : failureCode;
        detail = detail == null ? "" : detail;
        settingRestores = settingRestores == null ? List.of() : List.copyOf(settingRestores);
        setupOutcome = setupOutcome == null ? SetupOutcome.UNKNOWN : setupOutcome;
    }
}
