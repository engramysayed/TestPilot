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

    private static void restoreProp(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }
}
