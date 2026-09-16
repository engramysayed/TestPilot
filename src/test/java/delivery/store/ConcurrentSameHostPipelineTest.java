package delivery.store;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.job.ConversionJobRequest;
import delivery.job.DryRunConversionService;
import delivery.job.JobProgressTracker;
import delivery.job.TenantScope;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentSameHostPipelineTest {

    @Test
    public void concurrentHostedJobsKeepProofPackagesEvidenceHooksAndMemoryApart() throws Exception {
        Path root = Files.createTempDirectory("pipe-root");
        Path work = root.resolve("work");
        Path store = root.resolve("store");
        TenantId alice = TenantId.mint();
        TenantId bob = TenantId.mint();
        String host = "https://same.example.com/shop";
        ConversionJobRequest aliceReq = hostedRequest("prj_alice", "job_alice01", alice, work, store, host);
        ConversionJobRequest bobReq = hostedRequest("prj_bob", "job_bob00001", bob, work, store, host);

        seedCustomerFiles(store, alice, host, "data-alice-hook", "ALICE_MEMORY");
        seedCustomerFiles(store, bob, host, "data-bob-hook", "BOB_MEMORY");

        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Exception> error = new AtomicReference<>();
        Thread t1 = new Thread(() -> runDry(aliceReq, start, done, error));
        Thread t2 = new Thread(() -> runDry(bobReq, start, done, error));
        t1.start();
        t2.start();
        Assert.assertTrue(done.await(60, TimeUnit.SECONDS));
        Assert.assertNull(error.get(), error.get() == null ? "" : error.get().toString());

        Path aliceProject = ScopePaths.projectRoot(store, alice, "prj_alice");
        Path bobProject = ScopePaths.projectRoot(store, bob, "prj_bob");
        Assert.assertNotEquals(aliceProject, bobProject);
        Assert.assertTrue(Files.exists(aliceProject.resolve("versions/v1.zip")));
        Assert.assertTrue(Files.exists(bobProject.resolve("versions/v1.zip")));
        Assert.assertNotEquals(
                Files.readAllBytes(aliceProject.resolve("versions/v1.zip")).length,
                0);
        Assert.assertTrue(Files.isDirectory(ScopePaths.jobWorkDir(work, alice, "job_alice01")));
        Assert.assertTrue(Files.isDirectory(ScopePaths.jobWorkDir(work, bob, "job_bob00001")));
        Assert.assertEquals(
                PreferredHooksStore.load(store, alice, host).get(0),
                "data-alice-hook");
        Assert.assertEquals(
                PreferredHooksStore.load(store, bob, host).get(0),
                "data-bob-hook");
        Assert.assertTrue(Files.readString(DomainLocatorMemory.sharedFile(store, alice, host)).contains("ALICE_MEMORY"));
        Assert.assertTrue(Files.readString(DomainLocatorMemory.sharedFile(store, bob, host)).contains("BOB_MEMORY"));
        Assert.assertTrue(Files.readString(
                ScopePaths.jobWorkDir(work, alice, "job_alice01").resolve("evidence/shot.txt")).contains("ALICE_EVIDENCE"));
        Assert.assertTrue(Files.readString(
                ScopePaths.jobWorkDir(work, bob, "job_bob00001").resolve("evidence/shot.txt")).contains("BOB_EVIDENCE"));
        Assert.assertFalse(Files.readString(DomainLocatorMemory.sharedFile(store, alice, host)).contains("BOB"));
        Assert.assertFalse(Files.readString(DomainLocatorMemory.sharedFile(store, bob, host)).contains("ALICE"));
    }

    private static ConversionJobRequest hostedRequest(
            String projectId, String jobId, TenantId tenant, Path work, Path store, String host
    ) {
        return new ConversionJobRequest(
                projectId,
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                host,
                "",
                "",
                work,
                store,
                Path.of("customer-framework-template"),
                "NEW",
                "",
                "",
                false,
                false,
                AuthoringEngine.KEEL,
                PrecisionJobConfig.DEFAULTS,
                tenant,
                jobId,
                TenantScope.HOSTED
        );
    }

    private static void seedCustomerFiles(
            Path store, TenantId tenant, String host, String hook, String memory
    ) throws Exception {
        PreferredHooksStore.save(store, tenant, host, hook);
        Path mem = DomainLocatorMemory.sharedFile(store, tenant, host);
        Files.createDirectories(mem.getParent());
        Files.writeString(mem, "{\"slots\":[],\"sentinel\":\"" + memory + "\"}");
    }

    private static void runDry(
            ConversionJobRequest request,
            CyclicBarrier start,
            CountDownLatch done,
            AtomicReference<Exception> error
    ) {
        try {
            start.await(10, TimeUnit.SECONDS);
            new DryRunConversionService().run(request, new JobProgressTracker());
            Path evidence = delivery.identity.ScopePaths.jobWorkDir(
                    request.workDir(), request.tenantId(), request.jobId()).resolve("evidence");
            Files.createDirectories(evidence);
            String token = request.jobId().contains("alice") ? "ALICE_EVIDENCE" : "BOB_EVIDENCE";
            Files.writeString(evidence.resolve("shot.txt"), token);
        } catch (Exception e) {
            error.compareAndSet(null, e);
        } finally {
            done.countDown();
        }
    }
}
