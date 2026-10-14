package delivery.mobile.runner.permission;

import delivery.mobile.permission.PermissionGrant;

public interface DevicePermissionPort {
    void apply(String permission, PermissionGrant grant) throws PermissionApplyException;

    void clearAppData(String packageName) throws PermissionApplyException;

    void recordSetting(String key, String previous, String next);

    java.util.List<delivery.mobile.permission.DeviceSettingChange> restoreSettings();

    final class PermissionApplyException extends Exception {
        public PermissionApplyException(String msg) { super(msg); }
    }
}
