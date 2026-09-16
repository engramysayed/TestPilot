package delivery.net;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class WorkerNetworkGuardTest {

    @Test
    public void huntNavigateToLoopbackIsRejectedBeforeDriverGet() {
        delivery.hunt.HuntActionExecutor ex = new delivery.hunt.HuntActionExecutor(null);
        ex.setNetworkGuard(WorkerNetworkGuard.shared("https://shop.example.com"));
        List<Map<String, Object>> log = ex.executeAll(List.of(Map.of(
                "type", "navigate",
                "url", "http://127.0.0.1/admin"
        )));
        Assert.assertEquals(log.size(), 1);
        Assert.assertEquals(log.get(0).get("status"), "rejected");
        String reason = String.valueOf(log.get(0).get("reason"));
        Assert.assertTrue(reason.contains("loopback") || reason.contains("blocked"), reason);
    }

    @Test
    public void controlPlaneSecretsAreNotCopiedOntoBrowserJobs() {
        WorkerCredentialScope scope = WorkerCredentialScope.forBrowserJob(
                "qa-user", "target-secret", "ws_ab", "ChangeMeAdmin1!");
        Assert.assertEquals(scope.targetUsername(), "qa-user");
        Assert.assertEquals(scope.targetPassword(), "target-secret");
        Assert.assertFalse(scope.containsControlPlaneSecret());
        Assert.assertFalse(scope.exported().containsKey("adminPassword"));
        WorkerCredentialScope blank = WorkerCredentialScope.forBrowserJob(
                "", "", "ws_ab", "ChangeMeAdmin1!");
        Assert.assertEquals(blank.targetPassword(), "");
    }
}
