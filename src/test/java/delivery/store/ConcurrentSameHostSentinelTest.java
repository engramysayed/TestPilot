package delivery.store;

import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentSameHostSentinelTest {

    @Test
    public void overlappingJobIdsOnTheSameHostKeepDistinctCustomerSentinels() throws Exception {
        Path work = Files.createTempDirectory("same-host-work");
        TenantId alice = TenantId.mint();
        TenantId bob = TenantId.mint();
        Path aliceJob = ScopePaths.createJobWorkDir(work, alice, "job_overlap");
        Path bobJob = ScopePaths.createJobWorkDir(work, bob, "job_overlap");
        Files.writeString(aliceJob.resolve("TC_01.txt"), "ALICE_SENTINEL");
        Files.writeString(bobJob.resolve("TC_01.txt"), "BOB_SENTINEL");
        Assert.assertNotEquals(aliceJob, bobJob);
        Assert.assertEquals(Files.readString(aliceJob.resolve("TC_01.txt")), "ALICE_SENTINEL");
        Assert.assertEquals(Files.readString(bobJob.resolve("TC_01.txt")), "BOB_SENTINEL");
        Assert.assertFalse(Files.readString(aliceJob.resolve("TC_01.txt")).contains("BOB"));
        Assert.assertFalse(Files.readString(bobJob.resolve("TC_01.txt")).contains("ALICE"));
    }

    @Test
    public void concurrentPublicationOnOneProjectDoesNotDropAVersion() throws Exception {
        Path root = Files.createTempDirectory("pub-store");
        TenantId tenant = TenantId.mint();
        ProjectStore store = new ProjectStore(root, "https://same.example.com", tenant);
        Path fw = Files.createTempDirectory("fw-pub");
        Files.writeString(fw.resolve("README.md"), "fw");
        Path zipA = Files.createTempFile("a", ".zip");
        Path zipB = Files.createTempFile("b", ".zip");
        Files.writeString(zipA, "PACKAGE_A");
        Files.writeString(zipB, "PACKAGE_B");
        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Exception> error = new AtomicReference<>();
        Thread t1 = new Thread(() -> publish(store, fw, zipA, start, done, error));
        Thread t2 = new Thread(() -> publish(store, fw, zipB, start, done, error));
        t1.start();
        t2.start();
        Assert.assertTrue(done.await(20, TimeUnit.SECONDS));
        Assert.assertNull(error.get(), error.get() == null ? "" : error.get().toString());
        Path versions = store.projectRoot("prj_lock").resolve("versions");
        Assert.assertTrue(Files.exists(versions.resolve("v1.zip")));
        Assert.assertTrue(Files.exists(versions.resolve("v2.zip")));
        String v1 = Files.readString(versions.resolve("v1.zip"));
        String v2 = Files.readString(versions.resolve("v2.zip"));
        Assert.assertTrue(v1.equals("PACKAGE_A") || v1.equals("PACKAGE_B"));
        Assert.assertTrue(v2.equals("PACKAGE_A") || v2.equals("PACKAGE_B"));
        Assert.assertNotEquals(v1, v2);
    }

    private static void publish(
            ProjectStore store,
            Path fw,
            Path zip,
            CyclicBarrier start,
            CountDownLatch done,
            AtomicReference<Exception> error
    ) {
        try {
            start.await(5, TimeUnit.SECONDS);
            store.saveVersion("prj_lock", fw, zip);
        } catch (Exception e) {
            error.compareAndSet(null, e);
        } finally {
            done.countDown();
        }
    }
}
