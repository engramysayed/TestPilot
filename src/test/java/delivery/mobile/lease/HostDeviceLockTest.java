package delivery.mobile.lease;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * M1-04: host-scoped physical lock is keyed by (hostId, serialFingerprint), not tenant.
 */
public class HostDeviceLockTest {

    @Test
    public void lockPathIsHostScopedNotTenant() throws Exception {
        Path root = Files.createTempDirectory("host-lock-root");
        Path a = HostDeviceLock.lockPath(root, "host-1", "fp-abc");
        Path b = HostDeviceLock.lockPath(root, "host-1", "fp-abc");
        Assert.assertEquals(a, b);
        Assert.assertTrue(a.toString().contains("hosts"));
        Assert.assertTrue(a.toString().contains("devices"));
        Assert.assertFalse(a.toString().toLowerCase().contains("tenant"));
    }

    @Test
    public void twoAgentThreadsContendOnSamePhysicalIdentity() throws Exception {
        Path root = Files.createTempDirectory("host-lock-contend");
        Optional<HostDeviceLock> first = HostDeviceLock.tryAcquire(root, "host-x", "serial-fp-1");
        Assert.assertTrue(first.isPresent());

        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Optional<HostDeviceLock>> second = new AtomicReference<>();
        Thread t = new Thread(() -> {
            started.countDown();
            second.set(HostDeviceLock.tryAcquire(root, "host-x", "serial-fp-1"));
        });
        t.start();
        Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
        t.join(5_000);
        Assert.assertNotNull(second.get());
        Assert.assertTrue(second.get().isEmpty(), "second agent must not hold the same physical lock");

        first.get().close();
        Optional<HostDeviceLock> third = HostDeviceLock.tryAcquire(root, "host-x", "serial-fp-1");
        Assert.assertTrue(third.isPresent(), "lock frees after release");
        third.get().close();
    }

    @Test
    public void differentFingerprintsDoNotContend() throws Exception {
        Path root = Files.createTempDirectory("host-lock-diff");
        HostDeviceLock a = HostDeviceLock.tryAcquire(root, "host-x", "fp-1").orElseThrow();
        HostDeviceLock b = HostDeviceLock.tryAcquire(root, "host-x", "fp-2").orElseThrow();
        a.close();
        b.close();
    }
}
