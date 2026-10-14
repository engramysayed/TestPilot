package delivery.mobile.appium;

import delivery.mobile.MobileProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** AE-L2: real /status readiness (offline — no Appium binary). */
public class ProcessAppiumBackendReadyTest {

    @Test
    public void statusBodyReadyAcceptsAppium2ReadyTrue() {
        Assert.assertTrue(ProcessAppiumBackend.statusBodyReady(
                "{\"value\":{\"ready\":true,\"message\":\"ok\"}}"));
        Assert.assertFalse(ProcessAppiumBackend.statusBodyReady(
                "{\"value\":{\"ready\":false,\"message\":\"starting\"}}"));
        Assert.assertTrue(ProcessAppiumBackend.statusBodyReady(
                "{\"status\":0,\"value\":{}}"));
        Assert.assertFalse(ProcessAppiumBackend.statusBodyReady(""));
        Assert.assertFalse(ProcessAppiumBackend.statusBodyReady(null));
    }

    @Test
    public void rejectsNonLoopbackBaseUrl() {
        Assert.assertTrue(ProcessAppiumBackend.isOwnedLoopbackBaseUrl("http://127.0.0.1:4723"));
        Assert.assertTrue(ProcessAppiumBackend.isOwnedLoopbackBaseUrl("http://localhost:4723"));
        Assert.assertFalse(ProcessAppiumBackend.isOwnedLoopbackBaseUrl("http://10.0.0.5:4723"));
        Assert.assertFalse(ProcessAppiumBackend.httpStatusReady("http://10.0.0.5:4723"));
    }

    @Test
    public void startWaitsUntilReadyCheckerTrue() throws Exception {
        Path script = fakeAppiumScript();
        MobileProperties props = new MobileProperties();
        props.setAppiumPath(script.toAbsolutePath().toString());
        props.setAppiumStartTimeoutSeconds(5);
        ProcessAppiumBackend backend = new ProcessAppiumBackend(props);
        AtomicInteger probes = new AtomicInteger();
        backend.setReadyChecker(baseUrl -> {
            Assert.assertTrue(ProcessAppiumBackend.isOwnedLoopbackBaseUrl(baseUrl), baseUrl);
            return probes.incrementAndGet() >= 3;
        });
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4723, 4730);
        OwnedLoopbackPortAllocator.PortBundle bundle = alloc.allocateBundle("lease_ready@1");
        AppiumStartRequest req = new AppiumStartRequest(
                "lease_ready@1", "lease_ready", 1L, "serial", bundle, () -> false);
        OwnedAppiumProcess handle = backend.start(req);
        Assert.assertNotNull(handle);
        Assert.assertTrue(probes.get() >= 3, "must poll ready checker, not return after 50ms stub");
        Assert.assertTrue(backend.isAlive(handle));
        backend.stop(handle);
    }

    @Test
    public void startTimesOutWhenNeverReady() throws Exception {
        Path script = fakeAppiumScript();
        MobileProperties props = new MobileProperties();
        props.setAppiumPath(script.toAbsolutePath().toString());
        props.setAppiumStartTimeoutSeconds(1);
        ProcessAppiumBackend backend = new ProcessAppiumBackend(props);
        backend.setReadyChecker(baseUrl -> false);
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4723, 4730);
        OwnedLoopbackPortAllocator.PortBundle bundle = alloc.allocateBundle("lease_to@1");
        AppiumStartRequest req = new AppiumStartRequest(
                "lease_to@1", "lease_to", 1L, "serial", bundle, () -> false);
        try {
            backend.start(req);
            Assert.fail("expected SESSION_START_TIMEOUT");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.SESSION_START_TIMEOUT);
            Assert.assertTrue(e.getMessage().contains("/status") || e.getMessage().contains("timeout"),
                    e.getMessage());
        }
    }

    private static Path fakeAppiumScript() throws Exception {
        Path script = Files.createTempFile("fake-appium-", ".sh");
        Files.writeString(script, "#!/bin/sh\n# ignore Appium CLI args; stay alive for readiness poll\nexec sleep 30\n");
        script.toFile().setExecutable(true);
        return script;
    }
}
