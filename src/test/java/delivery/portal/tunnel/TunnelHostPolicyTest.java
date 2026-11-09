package delivery.portal.tunnel;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TunnelHostPolicyTest {

    @Test
    public void dedicatedHostIsolatesBothWays() {
        var ev = TunnelHostPolicy.evaluate("https://keel.example.com", "https://tunnel.keel.example.com");
        Assert.assertTrue(ev.enforce());
        Assert.assertEquals(TunnelHostPolicy.check(ev, "tunnel.keel.example.com", "/tunnel/p/abc/x"), 0);
        Assert.assertEquals(TunnelHostPolicy.check(ev, "keel.example.com", "/tunnel/p/abc/x"), 404,
                "relay must not be served on the portal origin");
        Assert.assertEquals(TunnelHostPolicy.check(ev, "tunnel.keel.example.com", "/api/projects"), 404,
                "portal API must not be served on the tunnel origin");
        Assert.assertEquals(TunnelHostPolicy.check(ev, "keel.example.com", "/api/projects"), 0);
    }

    @Test
    public void sameHostAsPortalDisablesRelay() {
        var ev = TunnelHostPolicy.evaluate("https://keel.example.com", "https://keel.example.com");
        Assert.assertTrue(ev.misconfigured());
        Assert.assertFalse(ev.relayAllowed());
        Assert.assertEquals(TunnelHostPolicy.check(ev, "keel.example.com", "/tunnel/p/abc/"), 404);
        Assert.assertEquals(TunnelHostPolicy.check(ev, "keel.example.com", "/dashboard"), 0, "portal still works");
    }

    @Test
    public void loopbackIsDevModeWithoutEnforcement() {
        var ev = TunnelHostPolicy.evaluate("http://localhost:8080", "http://127.0.0.1:8080");
        Assert.assertTrue(ev.devLoopback());
        Assert.assertTrue(ev.relayAllowed());
        Assert.assertEquals(TunnelHostPolicy.check(ev, "localhost", "/tunnel/p/abc/"), 0);
    }

    @Test
    public void unsetTunnelUrlRefusesRelayPaths() {
        var ev = TunnelHostPolicy.evaluate("https://keel.example.com", "");
        Assert.assertEquals(TunnelHostPolicy.check(ev, "keel.example.com", "/tunnel/p/abc/"), 404);
    }
}
