package delivery.net;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.List;

public class TargetNetworkPolicyTest {

    private final TargetNetworkPolicy shared = TargetNetworkPolicy.shared("https://shop.example.com/app");

    @Test
    public void sharedAllowsApprovedOriginAndBlocksPrivateDestinations() {
        Assert.assertTrue(shared.inspect("https://shop.example.com/checkout").allowed());
        Assert.assertTrue(shared.inspect("http://shop.example.com/login").allowed());
        Assert.assertFalse(shared.inspect("http://127.0.0.1/admin").allowed());
        Assert.assertFalse(shared.inspect("http://localhost:8080/").allowed());
        Assert.assertFalse(shared.inspect("http://10.0.0.8/internal").allowed());
        Assert.assertFalse(shared.inspect("http://192.168.1.1/").allowed());
        Assert.assertFalse(shared.inspect("http://172.16.5.4/").allowed());
        Assert.assertFalse(shared.inspect("http://169.254.1.1/").allowed());
        Assert.assertFalse(shared.inspect("http://169.254.169.254/latest/meta-data/").allowed());
        Assert.assertFalse(shared.inspect("http://[::1]/").allowed());
        Assert.assertFalse(shared.inspect("http://[fe80::1]/").allowed());
        Assert.assertFalse(shared.inspect("http://[fc00::1]/").allowed());
        Assert.assertFalse(shared.inspect("file:///etc/passwd").allowed());
        Assert.assertFalse(shared.inspect("javascript:alert(1)").allowed());
        Assert.assertFalse(shared.inspect("ftp://shop.example.com/").allowed());
        Assert.assertFalse(shared.inspect("https://other.example.com/").allowed(),
                "navigation must stay on the approved origin");
    }

    @Test
    public void sharedBlocksRedirectsAndPrivateSubresourcesButAllowsPublicCdn() {
        TargetNetworkPolicy.Decision redirect = shared.inspectRedirect(
                "http://127.0.0.1:9/steal", "https://shop.example.com/go");
        Assert.assertFalse(redirect.allowed());
        Assert.assertTrue(redirect.reason().contains("loopback")
                || redirect.reason().contains("private")
                || redirect.reason().contains("blocked"));
        Assert.assertFalse(shared.inspectSubresource("http://10.1.2.3/pixel.gif").allowed());
        Assert.assertTrue(shared.inspectSubresource("https://cdn.example.net/app.js").allowed());
        Assert.assertTrue(shared.inspectSubresource("https://shop.example.com/logo.png").allowed());
    }

    @Test
    public void sharedBlocksResolvedPrivateAddressesEvenWhenHostnameLooksPublic() throws Exception {
        InetAddress priv = InetAddress.getByName("10.0.0.9");
        TargetNetworkPolicy.Decision d = shared.inspectResolved("shop.example.com", List.of(priv));
        Assert.assertFalse(d.allowed());
    }

    @Test
    public void dedicatedAllowsConfiguredPrivateTargetWithoutBroadeningSharedPolicy() {
        TargetNetworkPolicy dedicated = TargetNetworkPolicy.dedicated(
                "http://10.0.0.8:8080/app", List.of("10.0.0.0/8"));
        Assert.assertTrue(dedicated.inspect("http://10.0.0.8:8080/login").allowed());
        Assert.assertTrue(dedicated.inspect("http://10.0.1.4/hook").allowed());
        Assert.assertFalse(dedicated.inspect("http://192.168.0.4/").allowed(),
                "CIDR allowlist must not open every private range");
        Assert.assertFalse(shared.inspect("http://10.0.0.8:8080/login").allowed(),
                "dedicated private access must not broaden shared-host policy");
    }

    @Test
    public void credentialsStayOnApprovedOrigin() {
        Assert.assertTrue(shared.credentialsAllowedAt("https://shop.example.com/login"));
        Assert.assertFalse(shared.credentialsAllowedAt("https://evil.example.net/login"));
        Assert.assertFalse(shared.credentialsAllowedAt("http://127.0.0.1/login"));
    }

    @Test
    public void loopbackApprovedOriginAllowsOnlyThatPort() {
        TargetNetworkPolicy local = TargetNetworkPolicy.shared("http://127.0.0.1:8080/app");
        Assert.assertTrue(local.inspect("http://127.0.0.1:8080/login").allowed());
        Assert.assertFalse(local.inspect("http://127.0.0.1:9/secrets").allowed());
        Assert.assertFalse(local.inspect("http://127.0.0.1/secrets").allowed());
    }

    @Test
    public void outboundWebhooksMayUsePublicCiHostsButCannotBypassRestrictedHops() {
        Assert.assertTrue(shared.inspectOutbound("https://ci.example/hooks/keel").allowed(),
                "CI webhooks are not limited to the browser approved origin");
        Assert.assertFalse(shared.inspectOutbound("http://169.254.169.254/latest/meta-data/").allowed());
        Assert.assertFalse(shared.inspectOutbound("http://127.0.0.1/hook").allowed());
        Assert.assertFalse(shared.inspectOutbound("http://10.0.0.8/hook").allowed());
        Assert.assertFalse(shared.inspectOutbound("file:///etc/passwd").allowed());
        TargetNetworkPolicy dedicated = TargetNetworkPolicy.dedicated(
                "http://127.0.0.1:8080/app", List.of("127.0.0.0/8"));
        Assert.assertTrue(dedicated.inspectOutbound("http://127.0.0.1:4079/hook").allowed());
        Assert.assertFalse(dedicated.inspectOutbound("http://169.254.169.254/latest/meta-data/").allowed());
    }
}
