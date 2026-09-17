package delivery.net;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

public class InstallNetworkBridgeTest {

    private String prevMode;
    private String prevCidrs;

    @org.testng.annotations.BeforeMethod
    public void snapshot() {
        prevMode = System.getProperty("delivery.install.mode");
        prevCidrs = System.getProperty("delivery.install.private-cidrs");
    }

    @AfterMethod(alwaysRun = true)
    public void restore() {
        restoreProp("delivery.install.mode", prevMode);
        restoreProp("delivery.install.private-cidrs", prevCidrs);
    }

    @Test
    public void dedicatedSpringConfigAllowsPrivateCidrOnThatInstall() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("install", Map.of(
                "delivery.install.mode", "dedicated",
                "delivery.install.private-cidrs", "10.0.0.0/8")));
        InstallNetworkBridge.apply(env);

        TargetNetworkPolicy policy = TargetNetworkPolicy.forJob("http://10.0.0.8:8080/app");
        Assert.assertEquals(policy.mode(), TargetNetworkPolicy.Mode.DEDICATED);
        Assert.assertTrue(policy.inspect("http://10.0.0.8:8080/login").allowed());
        Assert.assertFalse(policy.inspect("http://192.168.0.4/").allowed());
    }

    @Test
    public void sharedSpringConfigIgnoresDedicatedCidrs() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("install", Map.of(
                "delivery.install.mode", "shared",
                "delivery.install.private-cidrs", "10.0.0.0/8")));
        InstallNetworkBridge.apply(env);

        TargetNetworkPolicy policy = TargetNetworkPolicy.forJob("https://shop.example.com");
        Assert.assertEquals(policy.mode(), TargetNetworkPolicy.Mode.SHARED);
        Assert.assertFalse(policy.inspect("http://10.0.0.8:8080/login").allowed(),
                "shared install must not honor dedicated CIDRs from the same properties file");
    }

    @Test
    public void twoJobsOnThisJvmShareTheSameInstallMode() {
        InstallNetworkBridge.apply("dedicated", "10.0.0.0/8");
        TargetNetworkPolicy jobA = TargetNetworkPolicy.forJob("http://10.0.0.8:8080/app");
        TargetNetworkPolicy jobB = TargetNetworkPolicy.forJob("https://shop.example.com");
        Assert.assertEquals(jobA.mode(), TargetNetworkPolicy.Mode.DEDICATED);
        Assert.assertEquals(jobB.mode(), jobA.mode(),
                "install mode is JVM-wide; it must not vary by job approved origin");
    }

    @Test
    public void rejectsJobOrTenantScopedInstallOverrides() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("job-scope", Map.of(
                "delivery.install.mode", "shared",
                "delivery.job.install.mode", "dedicated")));
        Assert.assertThrows(IllegalStateException.class, () -> InstallNetworkBridge.apply(env));

        StandardEnvironment tenant = new StandardEnvironment();
        tenant.getPropertySources().addFirst(new MapPropertySource("tenant-scope", Map.of(
                "delivery.install.mode", "shared",
                "delivery.tenant.install.private-cidrs", "10.0.0.0/8")));
        Assert.assertThrows(IllegalStateException.class, () -> InstallNetworkBridge.apply(tenant));
    }

    private static void restoreProp(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }
}
