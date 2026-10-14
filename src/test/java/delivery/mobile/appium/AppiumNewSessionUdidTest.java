package delivery.mobile.appium;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * AE-L6 offline coverage: W3C caps include exact appium:udid; factory stores real NewSession id;
 * blank serial rejected; non-loopback baseUrl rejected; lifecycle still activates with mock transporter.
 */
public class AppiumNewSessionUdidTest {

    @Test
    public void capabilitiesJsonIncludesExactUdid() {
        String body = AppiumW3cCapabilities.newSessionBody("serial_ok");
        Assert.assertTrue(body.contains("\"appium:udid\":\"serial_ok\""), body);
        Assert.assertTrue(body.contains("\"platformName\":\"Android\""), body);
        Assert.assertTrue(body.contains("\"appium:automationName\":\"UiAutomator2\""), body);
        Assert.assertTrue(body.contains("\"alwaysMatch\""), body);
        Assert.assertFalse(body.contains("avd"), body);
        Assert.assertFalse(body.toLowerCase().contains("emulatorlaunch"), body);
    }

    @Test
    public void blankSerialRejected() {
        try {
            AppiumW3cCapabilities.newSessionBody("  ");
            Assert.fail("expected INVALID_STATE");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.INVALID_STATE);
        }
        try {
            AppiumW3cCapabilities.requireSerial(null);
            Assert.fail("expected INVALID_STATE");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.INVALID_STATE);
        }
    }

    @Test
    public void nonLoopbackBaseUrlRejected() {
        try {
            HttpAppiumSessionTransporter.requireOwnedLoopback("http://10.0.0.5:4723");
            Assert.fail("expected APPIUM_UNAVAILABLE");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.APPIUM_UNAVAILABLE);
        }
        try {
            HttpAppiumSessionTransporter.requireOwnedLoopback("https://127.0.0.1:4723");
            Assert.fail("expected APPIUM_UNAVAILABLE");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.APPIUM_UNAVAILABLE);
        }
        Assert.assertEquals(
                HttpAppiumSessionTransporter.requireOwnedLoopback("http://127.0.0.1:4723/"),
                "http://127.0.0.1:4723");
        Assert.assertEquals(
                HttpAppiumSessionTransporter.requireOwnedLoopback("http://localhost:4723"),
                "http://localhost:4723");
    }

    @Test
    public void parseSessionIdToleratesAppium2And1Shapes() {
        Assert.assertEquals(
                HttpAppiumSessionTransporter.parseSessionId(
                        "{\"value\":{\"sessionId\":\"real-appium-sid-123\",\"capabilities\":{}}}"),
                "real-appium-sid-123");
        Assert.assertEquals(
                HttpAppiumSessionTransporter.parseSessionId(
                        "{\"status\":0,\"sessionId\":\"legacy-sid\",\"value\":{}}"),
                "legacy-sid");
        Assert.assertNull(HttpAppiumSessionTransporter.parseSessionId("{\"value\":{}}"));
    }

    @Test
    public void factoryUsesRealNewSessionIdNotInventedSessPrefix() {
        ScriptedAppiumSessionTransporter transport = new ScriptedAppiumSessionTransporter();
        transport.setNextSessionId("real-appium-sid-123");
        AppiumSessionFactory factory = new AppiumSessionFactory(
                Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC), transport);
        OwnedAppiumProcess process = new OwnedAppiumProcess(
                "appium_ref1", 4242L, "lease@1", 4723, 8200, 9515, "http://127.0.0.1:4723");
        AppiumSessionRecord rec = factory.createSession("lease_ok", 1L, process, "serial_ok");
        Assert.assertEquals(rec.getSessionId(), "real-appium-sid-123");
        Assert.assertFalse(rec.getSessionId().startsWith("sess_"), "must not invent sess_* as primary id");
        Assert.assertEquals(rec.getDeviceSerial(), "serial_ok");
        Assert.assertEquals(transport.creates().size(), 1);
        Assert.assertTrue(transport.creates().get(0).body().contains("\"appium:udid\":\"serial_ok\""));
        Assert.assertEquals(transport.creates().get(0).deviceSerial(), "serial_ok");

        factory.quitSession("real-appium-sid-123");
        Assert.assertEquals(transport.deletes().size(), 1);
        Assert.assertEquals(transport.deletes().get(0).sessionId(), "real-appium-sid-123");
        Assert.assertEquals(rec.getState(), AppiumSessionRecord.State.QUIT);
    }

    @Test
    public void factoryRejectsBlankSerialWithoutCallingNetworkInvent() {
        ScriptedAppiumSessionTransporter transport = new ScriptedAppiumSessionTransporter();
        AppiumSessionFactory factory = new AppiumSessionFactory(Clock.systemUTC(), transport);
        OwnedAppiumProcess process = new OwnedAppiumProcess(
                "appium_ref1", 1L, "lease@1", 4723, 8200, 9515, "http://127.0.0.1:4723");
        try {
            factory.createSession("lease_blank", 1L, process, "");
            Assert.fail("expected INVALID_STATE");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.INVALID_STATE);
        }
        Assert.assertTrue(transport.creates().isEmpty(), "must not NewSession without serial");
    }
}
