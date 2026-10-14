package delivery.mobile.appium;

import delivery.mobile.MobileProperties;
import delivery.mobile.adb.AdbDeviceDiscovery;
import delivery.mobile.adb.ScriptedAdbBackend;
import delivery.mobile.lease.LeaseState;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M1-06 required coverage: crash→quarantine, cancel-while-starting, cleanup failure→quarantine,
 * owned-port isolation across jobs, no kill of unrelated PIDs / no adb kill-server.
 */
public class SupervisedAppiumLifecycleTest {
    private static final String INVENTORY = ""
            + "List of devices attached\n"
            + "serial_ok          device product:sdk model:Pixel device:pixel transport_id:1\n";

    private MobileProperties props;
    private ScriptedAdbBackend adb;
    private ScriptedAppiumBackend appium;
    private OwnedLoopbackPortAllocator ports;
    private AppiumSessionFactory sessions;
    private FakeLeaseLifecycleGate leases;
    private SupervisedAppiumLifecycle lifecycle;

    @BeforeMethod
    public void setUp() {
        props = new MobileProperties();
        props.setAppiumPath("appium");
        props.setAppiumPortRangeStart(4600);
        props.setAppiumPortRangeEnd(4699);
        adb = new ScriptedAdbBackend();
        adb.on("devices -l", INVENTORY);
        appium = new ScriptedAppiumBackend();
        appium.setAvailable(true);
        appium.setStartBehavior(ScriptedAppiumBackend.StartBehavior.SUCCESS);
        ports = new OwnedLoopbackPortAllocator(props);
        sessions = new AppiumSessionFactory();
        leases = new FakeLeaseLifecycleGate();
        MobilePreflight preflight = new MobilePreflight(new AdbDeviceDiscovery(props, adb), appium);
        lifecycle = new SupervisedAppiumLifecycle(props, preflight, ports, appium, sessions, leases);
    }

    @Test
    public void startActivatesLeaseWithSessionAndOwnedProcess() {
        leases.seedReserved("lease_ok", 1L, "dev_1");
        SupervisedAppiumLifecycle.Binding b = lifecycle.start("lease_ok", 1L, "serial_ok", () -> false);
        Assert.assertNotNull(b.session());
        Assert.assertNotNull(b.process());
        Assert.assertEquals(b.ports().server().getBindAddress(), OwnedPort.LOOPBACK);
        Assert.assertEquals(leases.getLease("lease_ok").getState(), LeaseState.ACTIVE.name());
        Assert.assertEquals(leases.getLease("lease_ok").getSessionRef(), b.session().getSessionId());
        Assert.assertEquals(leases.getLease("lease_ok").getOwnedProcessRef(), b.process().getProcessRef());
        lifecycle.teardown("lease_ok", 1L);
        Assert.assertTrue(leases.events().stream().anyMatch(e -> "acknowledgeCleanup".equals(e.type())));
    }

    @Test
    public void processCrashQuarantinesLease() {
        leases.seedReserved("lease_crash", 2L, "dev_1");
        SupervisedAppiumLifecycle.Binding b = lifecycle.start("lease_crash", 2L, "serial_ok", () -> false);
        appium.crash(b.process().getProcessRef());
        lifecycle.reconcileAlive("lease_crash");
        Assert.assertTrue(leases.wasQuarantined("lease_crash"));
        Assert.assertTrue(leases.events().stream().anyMatch(e ->
                "quarantine".equals(e.type()) && e.detail() != null && e.detail().contains("crashed")));
    }

    @Test
    public void cancelWhileStartingDoesNotActivateAndReleasesPorts() {
        leases.seedReserved("lease_cancel", 3L, "dev_1");
        appium.setStartBehavior(ScriptedAppiumBackend.StartBehavior.HANG_UNTIL_CANCEL);
        AtomicBoolean cancel = new AtomicBoolean(false);
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            cancel.set(true);
        });
        canceller.start();
        try {
            lifecycle.start("lease_cancel", 3L, "serial_ok", cancel::get);
            Assert.fail("expected CANCELLED_WHILE_STARTING");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.CANCELLED_WHILE_STARTING);
        }
        Assert.assertEquals(leases.getLease("lease_cancel").getState(), LeaseState.RESERVED.name(),
                "must not activate on cancel-while-starting");
        Assert.assertEquals(ports.ownedCount(SupervisedAppiumLifecycle.ownerKey("lease_cancel", 3L)), 0);
        Assert.assertNull(lifecycle.getBinding("lease_cancel"));
    }

    @Test
    public void cleanupFailureQuarantines() {
        leases.seedReserved("lease_cleanup", 4L, "dev_1");
        lifecycle.start("lease_cleanup", 4L, "serial_ok", () -> false);
        appium.setFailStop(true);
        try {
            lifecycle.teardown("lease_cleanup", 4L);
            Assert.fail("expected CLEANUP_FAILED");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.CLEANUP_FAILED);
        }
        Assert.assertTrue(leases.wasQuarantined("lease_cleanup"));
    }

    @Test
    public void ownedPortIsolationAcrossTwoJobs() {
        leases.seedReserved("lease_a", 5L, "dev_1");
        leases.seedReserved("lease_b", 6L, "dev_1");
        SupervisedAppiumLifecycle.Binding a = lifecycle.start("lease_a", 5L, "serial_ok", () -> false);
        SupervisedAppiumLifecycle.Binding b = lifecycle.start("lease_b", 6L, "serial_ok", () -> false);
        Assert.assertNotEquals(a.ports().server().getPort(), b.ports().server().getPort());
        Assert.assertNotEquals(a.process().getPid(), b.process().getPid());
        Assert.assertTrue(ports.isOwnedBy(a.ports().server().getPort(), a.ownerKey()));
        Assert.assertFalse(ports.isOwnedBy(a.ports().server().getPort(), b.ownerKey()));
        lifecycle.teardown("lease_a", 5L);
        // B still owns its ports; A's ports released
        Assert.assertEquals(ports.ownedCount(a.ownerKey()), 0);
        Assert.assertTrue(ports.ownedCount(b.ownerKey()) >= 3);
        lifecycle.teardown("lease_b", 6L);
    }

    @Test
    public void teardownKillsOnlyOwnedPidNotUnrelated() {
        leases.seedReserved("lease_own", 7L, "dev_1");
        leases.seedReserved("lease_other", 8L, "dev_1");
        SupervisedAppiumLifecycle.Binding own = lifecycle.start("lease_own", 7L, "serial_ok", () -> false);
        SupervisedAppiumLifecycle.Binding other = lifecycle.start("lease_other", 8L, "serial_ok", () -> false);
        long ownPid = own.process().getPid();
        long otherPid = other.process().getPid();
        lifecycle.teardown("lease_own", 7L);
        List<Long> killed = appium.killedPids();
        Assert.assertTrue(killed.contains(ownPid));
        Assert.assertFalse(killed.contains(otherPid), "must not kill unrelated job PID");
        // other still alive
        Assert.assertTrue(appium.isAlive(other.process()));
        lifecycle.teardown("lease_other", 8L);
    }

    @Test
    public void noAdbKillServerOnLifecyclePaths() {
        leases.seedReserved("lease_safe", 9L, "dev_1");
        lifecycle.start("lease_safe", 9L, "serial_ok", () -> false);
        lifecycle.teardown("lease_safe", 9L);
        // ADB inventory may be called for preflight — never kill-server
        for (List<String> call : adb.calls()) {
            Assert.assertFalse(AppiumProcessKillPolicy.containsForbiddenKillServer(call),
                    "adb kill-server forbidden: " + call);
            Assert.assertFalse(call.contains("kill-server"));
        }
        // ProcessAppiumBackend allowlisted command also clean
        ProcessAppiumBackend real = new ProcessAppiumBackend(props);
        OwnedLoopbackPortAllocator.PortBundle bundle = new OwnedLoopbackPortAllocator(4700, 4705)
                .allocateBundle("probe");
        List<String> cmd = real.buildCommand(new AppiumStartRequest(
                "probe", "probe", 1L, "serial_ok", bundle, () -> false));
        Assert.assertFalse(AppiumProcessKillPolicy.containsForbiddenKillServer(cmd));
        Set<String> lower = new HashSet<>();
        for (String a : cmd) {
            if (a != null) {
                lower.add(a.toLowerCase());
            }
        }
        Assert.assertFalse(lower.contains("kill-server"));
        Assert.assertFalse(lower.contains("adb"));
    }

    @Test
    public void unavailableServerSurfacesAppiumUnavailable() {
        leases.seedReserved("lease_unavail", 10L, "dev_1");
        appium.setStartBehavior(ScriptedAppiumBackend.StartBehavior.UNAVAILABLE);
        try {
            lifecycle.start("lease_unavail", 10L, "serial_ok", () -> false);
            Assert.fail("expected APPIUM_UNAVAILABLE");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.APPIUM_UNAVAILABLE);
        }
        Assert.assertEquals(leases.getLease("lease_unavail").getState(), LeaseState.RESERVED.name());
        Assert.assertEquals(ports.ownedCount(SupervisedAppiumLifecycle.ownerKey("lease_unavail", 10L)), 0);
    }

    @Test
    public void sessionStartTimeoutDoesNotActivate() {
        leases.seedReserved("lease_to", 11L, "dev_1");
        appium.setStartBehavior(ScriptedAppiumBackend.StartBehavior.TIMEOUT);
        try {
            lifecycle.start("lease_to", 11L, "serial_ok", () -> false);
            Assert.fail("expected SESSION_START_TIMEOUT");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.SESSION_START_TIMEOUT);
        }
        Assert.assertEquals(leases.getLease("lease_to").getState(), LeaseState.RESERVED.name());
    }
}
