package delivery.mobile.appium;

import delivery.mobile.MobileProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

/**
 * M1-06: never kill unrelated PIDs; never adb kill-server on code paths.
 */
public class AppiumProcessKillPolicyTest {

    @Test
    public void refusesNonOwnedPid() {
        try {
            AppiumProcessKillPolicy.assertMayKill(999L, Set.of(1L, 2L));
            Assert.fail("expected FORBIDDEN_KILL");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.FORBIDDEN_KILL);
        }
    }

    @Test
    public void detectsAdbKillServerArgs() {
        Assert.assertTrue(AppiumProcessKillPolicy.containsForbiddenKillServer(List.of("adb", "kill-server")));
        Assert.assertTrue(AppiumProcessKillPolicy.containsForbiddenKillServer(List.of("kill-server")));
        Assert.assertFalse(AppiumProcessKillPolicy.containsForbiddenKillServer(
                List.of("appium", "--address", "127.0.0.1", "--port", "4723")));
    }

    @Test
    public void processAppiumBackendCommandNeverIncludesKillServer() {
        MobileProperties props = new MobileProperties();
        props.setAppiumPath("appium");
        ProcessAppiumBackend backend = new ProcessAppiumBackend(props);
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4723, 4730);
        OwnedLoopbackPortAllocator.PortBundle bundle = alloc.allocateBundle("lease_cmd@1");
        AppiumStartRequest req = new AppiumStartRequest(
                "lease_cmd@1", "lease_cmd", 1L, "serial", bundle, () -> false);
        List<String> cmd = backend.buildCommand(req);
        Assert.assertFalse(AppiumProcessKillPolicy.containsForbiddenKillServer(cmd));
        Assert.assertEquals(cmd.get(0), "appium");
        Assert.assertTrue(cmd.contains(OwnedPort.LOOPBACK));
        Assert.assertFalse(cmd.stream().anyMatch(a -> a != null && a.toLowerCase().contains("kill-server")));
        // Explicit: no adb binary in Appium start command
        Assert.assertFalse(cmd.stream().anyMatch(a -> a != null && a.equals("adb")));
    }

    @Test
    public void scriptedBackendRefusesUnrelatedKill() {
        ScriptedAppiumBackend backend = new ScriptedAppiumBackend();
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4723, 4730);
        OwnedLoopbackPortAllocator.PortBundle bundle = alloc.allocateBundle("lease_k@1");
        OwnedAppiumProcess owned = backend.start(new AppiumStartRequest(
                "lease_k@1", "lease_k", 1L, "serial", bundle, () -> false));
        Assert.assertTrue(backend.isAlive(owned));
        try {
            backend.attemptKillUnrelated(owned.getPid() + 9999);
            Assert.fail("expected FORBIDDEN_KILL");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.FORBIDDEN_KILL);
        }
        // owned pid still not in killed list from the forbidden attempt
        Assert.assertFalse(backend.killedPids().contains(owned.getPid() + 9999));
        backend.stop(owned);
        Assert.assertEquals(backend.killedPids(), List.of(owned.getPid()));
    }
}
