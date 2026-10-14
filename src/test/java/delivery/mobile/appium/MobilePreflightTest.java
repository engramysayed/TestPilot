package delivery.mobile.appium;

import delivery.mobile.MobileProperties;
import delivery.mobile.adb.AdbDeviceDiscovery;
import delivery.mobile.adb.ScriptedAdbBackend;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * M1-06 preflight outcomes: DEVICE_OFFLINE / UNAUTHORIZED / APPIUM_UNAVAILABLE.
 */
public class MobilePreflightTest {
    private static final String INVENTORY = ""
            + "List of devices attached\n"
            + "serial_ok          device product:sdk model:Pixel device:pixel transport_id:1\n"
            + "serial_unauth      unauthorized usb:1-2 transport_id:2\n"
            + "serial_off         offline transport_id:3\n";

    private ScriptedAdbBackend adb;
    private ScriptedAppiumBackend appium;
    private MobilePreflight preflight;

    @BeforeMethod
    public void setUp() {
        MobileProperties props = new MobileProperties();
        adb = new ScriptedAdbBackend();
        adb.on("devices -l", INVENTORY);
        appium = new ScriptedAppiumBackend();
        appium.setAvailable(true);
        preflight = new MobilePreflight(new AdbDeviceDiscovery(props, adb), appium);
    }

    @Test
    public void okWhenAuthorizedAndAppiumAvailable() {
        MobilePreflightResult r = preflight.check("serial_ok");
        Assert.assertTrue(r.isOk());
        Assert.assertEquals(r.getOutcome(), PreflightOutcome.OK);
        Assert.assertEquals(r.codeName(), "OK");
    }

    @Test
    public void unauthorizedOutcome() {
        MobilePreflightResult r = preflight.check("serial_unauth");
        Assert.assertEquals(r.getOutcome(), PreflightOutcome.UNAUTHORIZED);
        Assert.assertEquals(r.codeName(), "UNAUTHORIZED");
        Assert.assertFalse(r.isOk());
    }

    @Test
    public void deviceOfflineOutcome() {
        MobilePreflightResult r = preflight.check("serial_off");
        Assert.assertEquals(r.getOutcome(), PreflightOutcome.DEVICE_OFFLINE);
        Assert.assertEquals(r.codeName(), "DEVICE_OFFLINE");
    }

    @Test
    public void missingSerialIsDeviceOffline() {
        MobilePreflightResult r = preflight.check("missing_serial");
        Assert.assertEquals(r.getOutcome(), PreflightOutcome.DEVICE_OFFLINE);
    }

    @Test
    public void appiumUnavailableOutcome() {
        appium.setAvailable(false);
        MobilePreflightResult r = preflight.check("serial_ok");
        Assert.assertEquals(r.getOutcome(), PreflightOutcome.APPIUM_UNAVAILABLE);
        Assert.assertEquals(r.codeName(), "APPIUM_UNAVAILABLE");
        Assert.assertFalse(r.isAppiumReachable());
    }
}
