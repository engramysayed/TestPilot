package drivers;

import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Chrome's "Save password?" bubble floats over the top-right of the page and is invisible to
 * Selenium screenshots, so any control in that corner (SauceDemo's cart icon) silently stops
 * being clickable after a login step. Only the password-manager prefs suppress it —
 * --disable-infobars covers the old infobar strip, not the bubble.
 */
public class ChromeFactoryOptionsTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> chromeOptionsMap() {
        ChromeOptions options = new ChromeFactory().buildOptions();
        Object raw = options.asMap().get(ChromeOptions.CAPABILITY);
        Assert.assertTrue(raw instanceof Map, "goog:chromeOptions must be present");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void disablesPasswordSaveBubble() {
        Object prefs = chromeOptionsMap().get("prefs");
        Assert.assertTrue(prefs instanceof Map, "chrome prefs must be set");
        Map<String, Object> map = (Map<String, Object>) prefs;

        Assert.assertEquals(map.get("credentials_enable_service"), Boolean.FALSE,
                "credentials_enable_service must be false or Chrome offers to save the password");
        Assert.assertEquals(map.get("profile.password_manager_enabled"), Boolean.FALSE,
                "profile.password_manager_enabled must be false");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void disablesPasswordLeakAndAutofillPrompts() {
        Map<String, Object> map = (Map<String, Object>) chromeOptionsMap().get("prefs");
        Assert.assertEquals(map.get("profile.password_manager_leak_detection"), Boolean.FALSE,
                "leak-detection bubble also covers the top-right corner");
        Assert.assertEquals(map.get("autofill.profile_enabled"), Boolean.FALSE,
                "autofill address bubble appears over checkout forms");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void keepsExistingHardening() {
        Map<String, Object> chrome = chromeOptionsMap();
        var args = (java.util.List<String>) chrome.get("args");
        Assert.assertTrue(args.contains("--disable-notifications"), "args=" + args);
        Assert.assertTrue(args.contains("--disable-popup-blocking"), "args=" + args);
    }
}
