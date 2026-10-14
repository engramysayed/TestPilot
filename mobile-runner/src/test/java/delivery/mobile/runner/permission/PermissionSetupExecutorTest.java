package delivery.mobile.runner.permission;

import delivery.mobile.permission.DataClearRequest;
import delivery.mobile.permission.DeviceSettingChange;
import delivery.mobile.permission.PermissionGrant;
import delivery.mobile.permission.PermissionPolicy;
import delivery.mobile.permission.PermissionSpec;
import delivery.mobile.result.SetupOutcome;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class PermissionSetupExecutorTest {

    static final class FakePort implements DevicePermissionPort {
        final List<String> applied = new ArrayList<>();
        boolean cleared;
        final List<DeviceSettingChange> pending = new ArrayList<>();

        @Override
        public void apply(String permission, PermissionGrant grant) {
            applied.add(permission + "=" + grant);
        }

        @Override
        public void clearAppData(String packageName) {
            cleared = true;
        }

        @Override
        public void recordSetting(String key, String previous, String next) {
            pending.add(new DeviceSettingChange(key, previous, next, false, ""));
        }

        @Override
        public List<DeviceSettingChange> restoreSettings() {
            List<DeviceSettingChange> out = new ArrayList<>();
            for (DeviceSettingChange c : pending) {
                out.add(new DeviceSettingChange(c.key(), c.previousValue(), c.newValue(), true,
                        "restored " + c.key() + " -> " + c.previousValue()));
            }
            pending.clear();
            return out;
        }
    }

    @Test
    public void appliesOverrides_noImplicitClearForFreshLogin() {
        FakePort port = new FakePort();
        PermissionPolicy policy = new PermissionPolicy(
                List.of(),
                List.of(new PermissionSpec("android.permission.CAMERA", false, PermissionGrant.ALLOW),
                        new PermissionSpec("android.permission.RECORD_AUDIO", false, PermissionGrant.DENY)),
                true);
        // FRESH_LOGIN path passes refused clear request
        var r = new PermissionSetupExecutor(port).apply(policy, DataClearRequest.refused());
        Assert.assertTrue(r.ok());
        Assert.assertFalse(r.dataCleared());
        Assert.assertEquals(port.applied.size(), 2);
        Assert.assertFalse(port.cleared);
        Assert.assertTrue(PermissionSetupExecutor.wouldImplicitlyClear(DataClearRequest.refused()) == false);
    }

    @Test
    public void explicitDataClear_whenOptedIn() {
        FakePort port = new FakePort();
        var r = new PermissionSetupExecutor(port).apply(
                PermissionPolicy.empty(),
                new DataClearRequest(true, "com.example.app", "author reset"));
        Assert.assertTrue(r.ok());
        Assert.assertTrue(r.dataCleared());
        Assert.assertTrue(port.cleared);
    }

    @Test
    public void unsupportedSpecial_blocksSetup() {
        FakePort port = new FakePort();
        PermissionPolicy policy = new PermissionPolicy(
                List.of(),
                List.of(new PermissionSpec("android.permission.WRITE_SECURE_SETTINGS", true, PermissionGrant.ALLOW)),
                true);
        var r = new PermissionSetupExecutor(port).apply(policy, DataClearRequest.refused());
        Assert.assertFalse(r.ok());
        Assert.assertEquals(r.setupOutcome(), SetupOutcome.BLOCKED);
        Assert.assertEquals(r.failureCode(), "PERMISSION_UNSUPPORTED");
        Assert.assertTrue(port.applied.isEmpty());
    }

    @Test
    public void deviceSettingRestorationDiagnostics() {
        FakePort port = new FakePort();
        port.recordSetting("animator_duration_scale", "1.0", "0");
        var r = new PermissionSetupExecutor(port).apply(PermissionPolicy.empty(), DataClearRequest.refused());
        Assert.assertTrue(r.ok());
        Assert.assertEquals(r.settingRestores().size(), 1);
        Assert.assertTrue(r.settingRestores().get(0).restored());
        Assert.assertTrue(r.settingRestores().get(0).restoreDiagnostic().contains("animator_duration_scale"));
    }
}
