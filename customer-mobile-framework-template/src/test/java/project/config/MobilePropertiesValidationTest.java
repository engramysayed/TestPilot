package project.config;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class MobilePropertiesValidationTest {

    @Test
    public void acceptsAndroidUiAutomator2WithUdid() {
        MobileProperties p = MobileProperties.fromMap(base());
        p.validatePlatform();
        Assert.assertEquals(p.udid(), "emulator-5554");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void rejectsNonAndroid() {
        Map<String, String> m = base();
        m.put("mobile.platformName", "iOS");
        MobileProperties.fromMap(m).validatePlatform();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void rejectsNonUiAutomator2() {
        Map<String, String> m = base();
        m.put("mobile.automationName", "Espresso");
        MobileProperties.fromMap(m).validatePlatform();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void requiresUdid() {
        Map<String, String> m = base();
        m.put("mobile.udid", "");
        MobileProperties.fromMap(m).validatePlatform();
    }

    @Test
    public void validateInstalledAppSource() {
        Map<String, String> m = base();
        m.put("mobile.appSource", "INSTALLED");
        m.put("mobile.appPackage", "com.example.app");
        m.put("mobile.appActivity", ".Main");
        MobileProperties.fromMap(m).validateAppSource();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void apkRequiresPath() {
        Map<String, String> m = base();
        m.put("mobile.appSource", "APK");
        m.put("mobile.appPath", "");
        MobileProperties.fromMap(m).validateAppSource();
    }

    @Test
    public void configBuildsCapabilityMapWithoutSecrets() {
        Map<String, String> m = base();
        m.put("mobile.serverUrl", "http://127.0.0.1:4723");
        m.put("mobile.appPackage", "com.example");
        m.put("mobile.appActivity", ".Main");
        MobileConfig cfg = new MobileConfig(MobileProperties.fromMap(m));
        Map<String, Object> caps = cfg.capabilityMap();
        Assert.assertEquals(caps.get("platformName"), "Android");
        Assert.assertEquals(caps.get("appium:udid"), "emulator-5554");
        Assert.assertEquals(caps.get("appium:autoGrantPermissions"), false);
        Assert.assertFalse(caps.containsKey("mobile.auth.profile"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void factoryFailsClearlyWithoutServerUrl() {
        Map<String, String> m = base();
        m.put("mobile.serverUrl", "");
        MobileConfig cfg = new MobileConfig(MobileProperties.fromMap(m));
        new project.drivers.AndroidDriverFactory().create(cfg);
    }

    private static Map<String, String> base() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("mobile.platformName", "Android");
        m.put("mobile.automationName", "UiAutomator2");
        m.put("mobile.udid", "emulator-5554");
        m.put("mobile.serverUrl", "http://127.0.0.1:4723");
        m.put("mobile.appSource", "INSTALLED");
        return m;
    }
}
