package delivery.net;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

public class WorkerPacTest {

    @AfterMethod(alwaysRun = true)
    public void clearThreadPac() {
        WorkerPac.clear();
    }

    @Test
    public void sharedPacDirectsApprovedHostAndBlackholesEverythingElse() {
        String pac = WorkerPac.script(TargetNetworkPolicy.shared("https://shop.example.com/app"));
        Assert.assertTrue(pac.contains("shop.example.com"), pac);
        Assert.assertTrue(pac.contains("PROXY 127.0.0.1:9"), pac);
        Assert.assertFalse(pac.contains("isInNet(host, '10.0.0.0'"),
                "shared PAC must not honor dedicated CIDRs: " + pac);
    }

    @Test
    public void dedicatedPacAllowsConfiguredCidrWithoutWideningSharedScript() {
        String dedicated = WorkerPac.script(TargetNetworkPolicy.dedicated(
                "http://10.0.0.8:8080/app", List.of("10.0.0.0/8")));
        Assert.assertTrue(dedicated.contains("isInNet(host, '10.0.0.8'")
                || dedicated.contains("isInNet(host, '10.0.0.0'"), dedicated);
        String shared = WorkerPac.script(TargetNetworkPolicy.shared("https://shop.example.com"));
        Assert.assertFalse(shared.contains("10.0.0.0"), shared);
    }

    @Test
    public void installForJobWritesReadablePac() throws Exception {
        var file = WorkerPac.installForJob("https://shop.example.com");
        Assert.assertNotNull(file);
        Assert.assertTrue(java.nio.file.Files.isRegularFile(file));
        String body = java.nio.file.Files.readString(file);
        Assert.assertTrue(body.contains("FindProxyForURL"), body);
        Assert.assertEquals(WorkerPac.installedFile(), file);
    }
}
