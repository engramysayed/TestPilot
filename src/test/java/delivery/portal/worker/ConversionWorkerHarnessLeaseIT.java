package delivery.portal.worker;

import delivery.agent.harness.AgentContextBuilder;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTaskRouter;
import delivery.agent.harness.SlowFakeAgentRuntime;
import delivery.agent.harness.convert.AgentHarnessJobBridge;
import delivery.agent.harness.convert.AgentStageIndexStore;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.jobs.LeaseKeeper;
import delivery.portal.model.JobRecord;
import delivery.portal.service.PortalStore;
import delivery.store.ProjectStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Convert validation hook + {@link LeaseKeeper} + progress-thread heartbeats (mirrors
 * {@link ConversionWorker#runWithProgressMirror}).
 */
public class ConversionWorkerHarnessLeaseIT {

    @Test
    public void slowValidation_renewsLeaseAndPublishCompletes() throws Exception {
        Path root = Files.createTempDirectory("harness-lease");
        Path zip = writePackageZip(root);
        Path fw = Files.createTempDirectory("fw-lease");
        Files.writeString(fw.resolve("README.md"), "x");

        JobRecord job = new JobRecord("job_lease", "proj", 1L, "NEW", root.resolve("x.xlsx"), "https://ex.invalid", "", "");
        job.setStatus(JobRecord.Status.RUNNING);
        String attempt = DurableJobClaim.newAttemptId();
        job.setAttemptId(attempt);
        job.setWorkerId("worker-lease");
        job.setCancelGeneration(0);
        Instant claimTime = Instant.now();
        job.setLeaseUntil(claimTime.plus(DurableJobClaim.DEFAULT_LEASE));

        DurableJobClaim.Lease lease = new DurableJobClaim.Lease(
                job.getJobId(), attempt, job.getWorkerId(), job.getLeaseUntil(), 0, DurableJobClaim.Stage.EMIT);

        AtomicInteger portalHeartbeats = new AtomicInteger();
        AtomicInteger keeperHeartbeats = new AtomicInteger();
        PortalStore portalStore = mock(PortalStore.class);
        when(portalStore.heartbeat(any(), any())).thenAnswer(inv -> {
            DurableJobClaim.Lease l = inv.getArgument(1);
            boolean ok = DurableJobClaim.heartbeat(job, l, Instant.now(), DurableJobClaim.DEFAULT_LEASE);
            if (ok) {
                portalHeartbeats.incrementAndGet();
            }
            return ok;
        });
        when(portalStore.ownsAttempt(job, lease)).thenReturn(true);
        when(portalStore.publishAttempt(any(), any(), any())).thenAnswer(inv -> ((java.util.concurrent.Callable<?>) inv.getArgument(2)).call());

        Instant leaseBefore = job.getLeaseUntil();

        try (LeaseKeeper keeper = new LeaseKeeper((jobId, l) -> {
            boolean ok = DurableJobClaim.heartbeat(job, l, Instant.now(), DurableJobClaim.DEFAULT_LEASE);
            if (ok) {
                keeperHeartbeats.incrementAndGet();
            }
            return ok;
        }, Duration.ofHours(1))) {
            keeper.track(job.getJobId(), lease);

            AgentHarnessProperties props = new AgentHarnessProperties();
            props.setEnabled(true);
            props.setFrameworkValidationEnabled(true);

            EnumMap<delivery.agent.harness.AgentProviderId, delivery.agent.harness.AgentRuntime> runtimes =
                    new EnumMap<>(delivery.agent.harness.AgentProviderId.class);
            runtimes.put(delivery.agent.harness.AgentProviderId.FAKE, new SlowFakeAgentRuntime(2_500));
            AgentHarnessCoordinator coordinator = new AgentHarnessCoordinator(
                    new AgentRunStore(),
                    new AgentTaskRouter(props),
                    new AgentContextBuilder(),
                    props,
                    runtimes);
            AgentHarnessJobBridge bridge = new AgentHarnessJobBridge(
                    props, coordinator, new AgentRunStore(), new AgentStageIndexStore());
            Path projectRoot = root.resolve("store/proj");
            Files.createDirectories(projectRoot);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread progress = new Thread(() -> {
                long last = 0L;
                while (running.get()) {
                    long now = System.currentTimeMillis();
                    if (now - last >= 400L) {
                        portalStore.heartbeat(job.getJobId(), lease);
                        last = now;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "progress-lease-test");
            progress.setDaemon(true);
            progress.start();

            String fingerprint = bridge.validateBeforePublish(
                    projectRoot, "proj", job.getJobId(), zip, Duration.ofMinutes(5), () -> false);

            running.set(false);
            progress.interrupt();
            progress.join(5_000);

            Assert.assertTrue(portalHeartbeats.get() >= 3,
                    "portal heartbeats during slow validation: " + portalHeartbeats.get());
            Assert.assertTrue(job.getLeaseUntil().isAfter(leaseBefore), "lease must be extended");
            Assert.assertEquals(job.getAttemptId(), attempt, "attempt must not change");
            Assert.assertEquals(job.getWorkerId(), "worker-lease");

            JobAttemptPublicationGuard guard = new JobAttemptPublicationGuard(portalStore, job.getJobId(), lease);
            ProjectStore store = new ProjectStore(root.resolve("store"));
            var saved = store.saveVersion("proj", fw, zip, null, new delivery.job.PublicationGuard() {
                public Optional<String> expectedZipFingerprint() {
                    return Optional.of(fingerprint);
                }

                public <T> T publish(java.util.concurrent.Callable<T> action) throws Exception {
                    return guard.publish(action);
                }
            });
            Assert.assertEquals(saved.version(), 1);
        }
    }

    private static Path writePackageZip(Path dir) throws Exception {
        Path zip = dir.resolve("package.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("src/App.java"));
            zos.write("class App {}".getBytes());
            zos.closeEntry();
        }
        return zip;
    }
}
