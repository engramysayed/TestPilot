package delivery.mobile.adb;

import delivery.mobile.MobileProperties;
import delivery.mobile.model.MobileAuthorizedState;
import delivery.mobile.model.MobileDeviceReadiness;
import delivery.mobile.model.MobileVerification;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * M1-03 acceptance: multiple devices, unauthorized/offline, missing serial, Unicode names,
 * unavailable SDK, malicious package/serial inputs; no first-device fallback.
 */
public class AdbDeviceDiscoveryTest {
    private static final String MULTI = ""
            + "List of devices attached\n"
            + "emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1\n"
            + "R58M45ABCDE            device usb:1-1 product:beyond1lte model:SM_G973F device:beyond1 transport_id:2\n"
            + "XYZUNAUTHORIZED        unauthorized usb:1-2 transport_id:3\n"
            + "OFFLINE123             offline transport_id:4\n";

    private MobileProperties props;
    private ScriptedAdbBackend backend;
    private AdbDeviceDiscovery discovery;

    @BeforeMethod
    public void setUp() {
        props = new MobileProperties();
        props.setAdbPath("adb");
        props.setAdbTimeoutSeconds(15);
        backend = new ScriptedAdbBackend();
        discovery = new AdbDeviceDiscovery(props, backend);
    }

    @Test
    public void listsMultipleDevicesIncludingUnauthorizedAndOffline() throws Exception {
        backend.on("devices -l", MULTI);
        List<AdbDeviceInfo> devices = discovery.listDevices();
        Assert.assertEquals(devices.size(), 4);
        Assert.assertEquals(devices.get(0).getSerial(), "emulator-5554");
        Assert.assertEquals(devices.get(0).toAuthorizedState(), MobileAuthorizedState.AUTHORIZED);
        Assert.assertEquals(devices.get(0).toReadiness(), MobileDeviceReadiness.READY);
        Assert.assertEquals(devices.get(2).getSerial(), "XYZUNAUTHORIZED");
        Assert.assertEquals(devices.get(2).toAuthorizedState(), MobileAuthorizedState.UNAUTHORIZED);
        Assert.assertEquals(devices.get(2).toReadiness(), MobileDeviceReadiness.NOT_AUTHORIZED);
        Assert.assertEquals(devices.get(3).getSerial(), "OFFLINE123");
        Assert.assertEquals(devices.get(3).toAuthorizedState(), MobileAuthorizedState.OFFLINE);
        Assert.assertEquals(devices.get(3).toReadiness(), MobileDeviceReadiness.OFFLINE);
        Assert.assertFalse(discovery.allowsFirstDeviceFallback());
    }

    @Test
    public void requireDeviceMatchesExactSerialAmongMany() throws Exception {
        backend.on("devices -l", MULTI);
        AdbDeviceInfo info = discovery.requireDevice("R58M45ABCDE");
        Assert.assertEquals(info.getSerial(), "R58M45ABCDE");
        Assert.assertEquals(info.getModel(), "SM_G973F");
        Assert.assertNotEquals(info.getSerial(), "emulator-5554");
    }

    @Test
    public void missingSerialDoesNotFallBackToFirstDevice() throws Exception {
        backend.on("devices -l", MULTI);
        try {
            discovery.requireDevice("DOES-NOT-EXIST");
            Assert.fail("expected DEVICE_MISSING");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.DEVICE_MISSING);
            Assert.assertTrue(e.getMessage().toLowerCase().contains("no first-device fallback")
                    || e.getMessage().contains("not present"));
        }
        // ensure we never issued a -s command against another device
        for (List<String> call : backend.calls()) {
            Assert.assertFalse(call.contains("-s") && call.contains("emulator-5554"),
                    "must not target first device after miss: " + call);
        }
    }

    @Test
    public void blankSerialRejectedWithoutSpawningAdb() {
        try {
            discovery.requireDevice("  ");
            Assert.fail("expected INVALID_SERIAL");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.INVALID_SERIAL);
        }
        Assert.assertTrue(backend.calls().isEmpty(), "adb must not run for blank serial");
    }

    @Test
    public void unauthorizedSerialExposedAndBlocksOnlineRequirement() throws Exception {
        backend.on("devices -l", MULTI);
        AdbDeviceInfo info = discovery.requireDevice("XYZUNAUTHORIZED");
        Assert.assertEquals(info.toAuthorizedState(), MobileAuthorizedState.UNAUTHORIZED);
        try {
            discovery.requireOnlineAuthorized("XYZUNAUTHORIZED");
            Assert.fail("expected DEVICE_UNAUTHORIZED");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.DEVICE_UNAUTHORIZED);
        }
    }

    @Test
    public void offlineSerialExposedAndBlocksOnlineRequirement() throws Exception {
        backend.on("devices -l", MULTI);
        AdbDeviceInfo info = discovery.requireDevice("OFFLINE123");
        Assert.assertEquals(info.toAuthorizedState(), MobileAuthorizedState.OFFLINE);
        try {
            discovery.requireOnlineAuthorized("OFFLINE123");
            Assert.fail("expected DEVICE_OFFLINE");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.DEVICE_OFFLINE);
        }
    }

    @Test
    public void unicodeDisplayNamePreservedAndDisplayOnly() throws Exception {
        String arabicModel = "هاتف_اختبار";
        String raw = ""
                + "List of devices attached\n"
                + "SERIALUNICODE          device product:p model:" + arabicModel + " device:d transport_id:9\n"
                + "emulator-5554          device product:sdk model:emu device:e transport_id:1\n";
        backend.on("devices -l", raw);
        AdbDeviceInfo info = discovery.requireDevice("SERIALUNICODE");
        Assert.assertEquals(info.getDisplayName(), arabicModel);
        Assert.assertEquals(info.getModel(), arabicModel);
        // Selection is by serial, not by Unicode display name
        try {
            discovery.requireDevice(arabicModel);
            Assert.fail("display name must not be accepted as serial");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.INVALID_SERIAL);
        }
    }

    @Test
    public void unavailableSdkSurfacedHonestly() {
        backend.unavailable();
        try {
            discovery.listDevices();
            Assert.fail("expected SDK_UNAVAILABLE");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.SDK_UNAVAILABLE);
        }
    }

    @Test
    public void maliciousSerialRejected() {
        String[] bad = {
                "emu;rm -rf /",
                "emu|cat",
                "emu$(reboot)",
                "emu`id`",
                "-s",
                "../serial",
                "emu\n5554",
                "emu 5554",
                "emu&offline"
        };
        for (String s : bad) {
            try {
                discovery.requireDevice(s);
                Assert.fail("expected INVALID_SERIAL for: " + s);
            } catch (AdbInventoryException e) {
                Assert.assertEquals(e.getCode(), AdbErrorCode.INVALID_SERIAL, s);
            }
        }
        Assert.assertTrue(backend.calls().isEmpty());
    }

    @Test
    public void maliciousPackageRejectedBeforeShell() throws Exception {
        backend.on("devices -l", MULTI);
        String[] bad = {
                "com.example;reboot",
                "com.example|id",
                "com.example`id`",
                "../com.example",
                "com example.app",
                "com.example\napp",
                "-package",
                "single"
        };
        for (String p : bad) {
            try {
                discovery.resolveInstalledPackage("emulator-5554", p);
                Assert.fail("expected INVALID_PACKAGE for: " + p);
            } catch (AdbInventoryException e) {
                Assert.assertEquals(e.getCode(), AdbErrorCode.INVALID_PACKAGE, p);
            }
        }
        // devices -l may run after serial validation; ensure no shell pm for bad packages
        for (List<String> call : backend.calls()) {
            Assert.assertFalse(call.contains("pm"), "pm must not run for malicious package: " + call);
            Assert.assertFalse(call.contains("dumpsys"), "dumpsys must not run for malicious package: " + call);
        }
    }

    @Test
    public void installedPackageMetadataIsObserved() throws Exception {
        backend.on("devices -l", MULTI);
        backend.on("-s emulator-5554 shell getprop ro.product.model", "Pixel Test");
        backend.on("-s emulator-5554 shell getprop ro.build.version.sdk", "34");
        backend.on("-s emulator-5554 shell getprop ro.product.cpu.abi", "arm64-v8a");
        backend.on("-s emulator-5554 shell pm path com.example.app",
                "package:/data/app/~~x/com.example.app-abc/base.apk\n");
        backend.on("-s emulator-5554 shell dumpsys package com.example.app",
                "Packages:\n"
                        + "  Package [com.example.app]\n"
                        + "    versionCode=42 minSdk=26 targetSdk=34\n"
                        + "    versionName=1.2.3-عربي\n");

        InstalledPackageMetadata meta = discovery.resolveInstalledPackage("emulator-5554", "com.example.app");
        Assert.assertEquals(meta.getSerial(), "emulator-5554");
        Assert.assertEquals(meta.getPackageName(), "com.example.app");
        Assert.assertEquals(meta.getVersionCode(), Long.valueOf(42L));
        Assert.assertEquals(meta.getVersionName(), "1.2.3-عربي");
        Assert.assertEquals(meta.getVerification(), MobileVerification.OBSERVED.name());
        // All device-scoped calls must include exact -s serial
        boolean sawPm = false;
        for (List<String> call : backend.calls()) {
            if (call.contains("pm") || call.contains("dumpsys") || call.contains("getprop")) {
                Assert.assertEquals(call.get(0), "-s");
                Assert.assertEquals(call.get(1), "emulator-5554");
                sawPm = sawPm || call.contains("pm");
            }
        }
        Assert.assertTrue(sawPm);
    }

    @Test
    public void missingInstalledPackageOnSelectedSerial() throws Exception {
        backend.on("devices -l", MULTI);
        backend.on("-s emulator-5554 shell getprop ro.product.model", "emu");
        backend.on("-s emulator-5554 shell getprop ro.build.version.sdk", "34");
        backend.on("-s emulator-5554 shell getprop ro.product.cpu.abi", "x86_64");
        backend.on("-s emulator-5554 shell pm path com.missing.app", "");
        try {
            discovery.resolveInstalledPackage("emulator-5554", "com.missing.app");
            Assert.fail("expected PACKAGE_NOT_FOUND");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.PACKAGE_NOT_FOUND);
        }
    }

    @Test
    public void resolveInstalledPackageOnUnauthorizedDoesNotTouchOtherDevice() throws Exception {
        backend.on("devices -l", MULTI);
        try {
            discovery.resolveInstalledPackage("XYZUNAUTHORIZED", "com.example.app");
            Assert.fail("expected DEVICE_UNAUTHORIZED");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.DEVICE_UNAUTHORIZED);
        }
        for (List<String> call : backend.calls()) {
            Assert.assertFalse(call.contains("pm"));
            Assert.assertFalse(call.contains("emulator-5554") && call.contains("-s"));
        }
    }

    @Test
    public void enrichmentFillsApiAndAbiWhenOnline() throws Exception {
        backend.on("devices -l", MULTI);
        backend.on("-s R58M45ABCDE shell getprop ro.product.model", "Galaxy S10");
        backend.on("-s R58M45ABCDE shell getprop ro.build.version.sdk", "29");
        backend.on("-s R58M45ABCDE shell getprop ro.product.cpu.abi", "arm64-v8a");
        AdbDeviceInfo info = discovery.requireDevice("R58M45ABCDE", true);
        Assert.assertEquals(info.getApiLevel(), Integer.valueOf(29));
        Assert.assertEquals(info.getAbi(), "arm64-v8a");
        Assert.assertEquals(info.getModel(), "Galaxy S10");
        Assert.assertEquals(info.getDisplayName(), "Galaxy S10");
    }

    @Test
    public void parserIgnoresDaemonNoiseAndEmptyInventory() {
        List<AdbDeviceInfo> empty = AdbDevicesParser.parse("List of devices attached\n\n");
        Assert.assertTrue(empty.isEmpty());
        List<AdbDeviceInfo> noise = AdbDevicesParser.parse(
                "* daemon not running; starting now at tcp:5037\n"
                        + "* daemon started successfully\n"
                        + "List of devices attached\n");
        Assert.assertTrue(noise.isEmpty());
    }

    @Test
    public void selectedSerialDisappearsDoesNotPickNeighbor() throws Exception {
        java.util.concurrent.atomic.AtomicInteger devicesCalls = new java.util.concurrent.atomic.AtomicInteger();
        backend.on("devices -l", args -> {
            if (devicesCalls.getAndIncrement() == 0) {
                return MULTI;
            }
            return "List of devices attached\nemulator-5554          device product:sdk model:emu device:e transport_id:1\n";
        });
        AdbDeviceInfo first = discovery.requireDevice("R58M45ABCDE");
        Assert.assertEquals(first.getSerial(), "R58M45ABCDE");
        try {
            discovery.requireDevice("R58M45ABCDE");
            Assert.fail("expected DEVICE_MISSING after disconnect");
        } catch (AdbInventoryException e) {
            Assert.assertEquals(e.getCode(), AdbErrorCode.DEVICE_MISSING);
            Assert.assertFalse(discovery.allowsFirstDeviceFallback());
        }
    }
}
