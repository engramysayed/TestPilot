package delivery.portal.api;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TunnelPublicProxyCookieTest {

    @Test
    public void portalSessionCookieNotForwardedToTunneledApp() {
        Assert.assertEquals(
                TunnelPublicProxyController.stripPortalCookies("JSESSIONID=abc; app_sid=1; remember-me=x; theme=dark"),
                "app_sid=1; theme=dark");
        Assert.assertEquals(TunnelPublicProxyController.stripPortalCookies("JSESSIONID=abc"), "");
    }

    @Test
    public void tunneledAppCannotOverwritePortalSession() {
        Assert.assertTrue(TunnelPublicProxyController.setsPortalCookie("JSESSIONID=evil; Path=/; HttpOnly"));
        Assert.assertFalse(TunnelPublicProxyController.setsPortalCookie("app_sid=1; Path=/"));
    }
}
