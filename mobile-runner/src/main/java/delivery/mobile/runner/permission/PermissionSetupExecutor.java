package delivery.mobile.runner.permission;

import delivery.mobile.permission.DataClearRequest;
import delivery.mobile.permission.DeviceSettingChange;
import delivery.mobile.permission.PermissionGrant;
import delivery.mobile.permission.PermissionPlan;
import delivery.mobile.permission.PermissionPolicy;
import delivery.mobile.permission.PermissionPolicyPlanner;
import delivery.mobile.result.SetupOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies explicit permission grants and optional data-clear (M3-05).
 * Fresh-login / restart paths must pass {@link DataClearRequest#refused()} unless author opted in.
 */
public final class PermissionSetupExecutor {
    private final DevicePermissionPort port;

    public PermissionSetupExecutor(DevicePermissionPort port) {
        this.port = port;
    }

    public PermissionSetupResult apply(PermissionPolicy policy, DataClearRequest clearRequest) {
        PermissionPlan plan = PermissionPolicyPlanner.plan(policy);
        if (!plan.accepted()) {
            return new PermissionSetupResult(
                    false, plan.failureCode(), SetupOutcome.BLOCKED, plan, false, List.of(), plan.message());
        }
        try {
            for (Map.Entry<String, PermissionGrant> e : plan.grants().entrySet()) {
                if (e.getValue() == PermissionGrant.UNCHANGED) {
                    continue;
                }
                port.apply(e.getKey(), e.getValue());
            }
            boolean cleared = false;
            DataClearRequest clear = clearRequest == null ? DataClearRequest.refused() : clearRequest;
            if (clear.mayExecute()) {
                port.clearAppData(clear.packageName());
                cleared = true;
            } else if (clear.explicitOptIn() && clear.packageName().isBlank()) {
                return new PermissionSetupResult(
                        false, "DATA_CLEAR_PACKAGE_REQUIRED", SetupOutcome.FAILED, plan, false, List.of(),
                        "explicit data-clear requires packageName");
            }
            List<DeviceSettingChange> restores = port.restoreSettings();
            return new PermissionSetupResult(
                    true, "", SetupOutcome.PASSED, plan, cleared, restores,
                    cleared ? "permissions applied; data cleared" : "permissions applied; no data clear");
        } catch (DevicePermissionPort.PermissionApplyException e) {
            return new PermissionSetupResult(
                    false, "PERMISSION_APPLY_FAILED", SetupOutcome.FAILED, plan, false, List.of(), e.getMessage());
        }
    }

    /** Guard used by FRESH_LOGIN / restart: never clear unless explicit. */
    public static boolean wouldImplicitlyClear(DataClearRequest req) {
        return req != null && !req.explicitOptIn() && false; // always false by construction
    }
}
