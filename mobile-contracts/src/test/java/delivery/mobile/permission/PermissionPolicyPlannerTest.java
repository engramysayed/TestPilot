package delivery.mobile.permission;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PermissionPolicyPlannerTest {

    @Test
    public void allowDenyCaseOverridesWin() {
        PermissionPolicy policy = new PermissionPolicy(
                List.of(new PermissionSpec("android.permission.CAMERA", false, PermissionGrant.ALLOW)),
                List.of(new PermissionSpec("android.permission.CAMERA", false, PermissionGrant.DENY),
                        new PermissionSpec("android.permission.RECORD_AUDIO", false, PermissionGrant.ALLOW)),
                true);
        PermissionPlan plan = PermissionPolicyPlanner.plan(policy);
        Assert.assertTrue(plan.accepted());
        Assert.assertEquals(plan.grants().get("android.permission.CAMERA"), PermissionGrant.DENY);
        Assert.assertEquals(plan.grants().get("android.permission.RECORD_AUDIO"), PermissionGrant.ALLOW);
    }

    @Test
    public void unsupportedSpecialPermission_rejected() {
        PermissionPolicy policy = new PermissionPolicy(
                List.of(),
                List.of(new PermissionSpec("android.permission.WRITE_SECURE_SETTINGS", true, PermissionGrant.ALLOW)),
                true);
        PermissionPlan plan = PermissionPolicyPlanner.plan(policy);
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), PermissionPolicy.PERMISSION_UNSUPPORTED);
        Assert.assertTrue(plan.message().contains("WRITE_SECURE_SETTINGS"));
    }

    @Test
    public void broadGrantAll_refused() {
        PermissionPolicy policy = new PermissionPolicy(
                List.of(new PermissionSpec("*", false, PermissionGrant.ALLOW)),
                List.of(),
                false);
        PermissionPlan plan = PermissionPolicyPlanner.plan(policy);
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), "PERMISSION_BROAD_GRANT_REFUSED");
    }

    @Test
    public void dataClearRequiresExplicitOptIn() {
        Assert.assertFalse(DataClearRequest.refused().mayExecute());
        Assert.assertFalse(new DataClearRequest(false, "com.ex", "fresh login").mayExecute());
        Assert.assertTrue(new DataClearRequest(true, "com.ex", "author opted in").mayExecute());
    }

    @Test
    public void supportedSpecialAllowlisted() {
        PermissionPolicy policy = new PermissionPolicy(
                List.of(),
                List.of(new PermissionSpec(
                        "android.permission.ACCESS_NOTIFICATION_POLICY", true, PermissionGrant.ALLOW)),
                true);
        Assert.assertTrue(PermissionPolicyPlanner.plan(policy).accepted());
    }
}
