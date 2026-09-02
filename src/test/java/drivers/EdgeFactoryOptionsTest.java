package drivers;

import org.openqa.selenium.edge.EdgeOptions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Edge is Chromium-based; the same save-password bubble can cover the top-right after login.
 */
public class EdgeFactoryOptionsTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> edgeOptionsMap() {
        EdgeOptions options = new EdgeFactory().buildOptions();
        Object raw = options.asMap().get(EdgeOptions.CAPABILITY);
        Assert.assertTrue(raw instanceof Map, "ms:edgeOptions must be present");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void disablesPasswordSaveBubble() {
        Object prefs = edgeOptionsMap().get("prefs");
        Assert.assertTrue(prefs instanceof Map, "edge prefs must be set");
        Map<String, Object> map = (Map<String, Object>) prefs;

        Assert.assertEquals(map.get("credentials_enable_service"), Boolean.FALSE);
        Assert.assertEquals(map.get("profile.password_manager_enabled"), Boolean.FALSE);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void disablesPasswordLeakAndAutofillPrompts() {
        Map<String, Object> map = (Map<String, Object>) edgeOptionsMap().get("prefs");
        Assert.assertEquals(map.get("profile.password_manager_leak_detection"), Boolean.FALSE);
        Assert.assertEquals(map.get("autofill.profile_enabled"), Boolean.FALSE);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void keepsExistingHardening() {
        Map<String, Object> edge = edgeOptionsMap();
        var args = (java.util.List<String>) edge.get("args");
        Assert.assertTrue(args.contains("--disable-notifications"), "args=" + args);
        Assert.assertTrue(args.contains("--disable-popup-blocking"), "args=" + args);
    }
}
