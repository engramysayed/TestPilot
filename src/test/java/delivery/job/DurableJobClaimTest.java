package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class DurableJobClaimTest {

    @Test
    public void secondWorkerCannotClaimWhileLeaseIsValid() {
        JobRecord job = queued("job_a");
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        Optional<DurableJobClaim.Lease> first = DurableJobClaim.tryClaim(job, "wkr_1", now, Duration.ofSeconds(30));
        Assert.assertTrue(first.isPresent());
        Assert.assertEquals(job.getStatus(), JobRecord.Status.RUNNING);
        Optional<DurableJobClaim.Lease> second = DurableJobClaim.tryClaim(
                job, "wkr_2", now.plusSeconds(5), Duration.ofSeconds(30));
        Assert.assertTrue(second.isEmpty());
        Assert.assertEquals(job.getAttemptId(), first.get().attemptId());
    }

    @Test
    public void expiredAdmittedLeaseIsReclaimableAndStaleAttemptCannotPublish() {
        JobRecord job = queued("job_b");
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        DurableJobClaim.Lease stale = DurableJobClaim.tryClaim(job, "wkr_1", now, Duration.ofSeconds(10)).orElseThrow();
        Instant later = now.plusSeconds(11);
        Assert.assertTrue(DurableJobClaim.reconcileExpired(job, later));
        Assert.assertEquals(job.getStatus(), JobRecord.Status.QUEUED);
        DurableJobClaim.Lease fresh = DurableJobClaim.tryClaim(job, "wkr_2", later, Duration.ofSeconds(10)).orElseThrow();
        Assert.assertFalse(DurableJobClaim.mayPublish(job, stale));
        Assert.assertTrue(DurableJobClaim.mayPublish(job, fresh));
        job.setStatus(JobRecord.Status.COMPLETED);
        Assert.assertFalse(DurableJobClaim.mayPublish(job, fresh), "duplicate complete must not publish twice");
    }

    @Test
    public void expiredBrowserLeaseIsUncertainAndNotReplayed() {
        JobRecord job = queued("job_c");
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        DurableJobClaim.Lease lease = DurableJobClaim.tryClaim(job, "wkr_1", now, Duration.ofSeconds(10)).orElseThrow();
        DurableJobClaim.markStage(job, lease.attemptId(), DurableJobClaim.Stage.BROWSER);
        Assert.assertTrue(DurableJobClaim.reconcileExpired(job, now.plusSeconds(11)));
        Assert.assertEquals(job.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(job.getError(), DurableJobClaim.INTERRUPTED_UNCERTAIN);
        Assert.assertTrue(DurableJobClaim.tryClaim(job, "wkr_2", now.plusSeconds(12), Duration.ofSeconds(10)).isEmpty());
    }

    @Test
    public void cancelGenerationFencesPublishAndCrashBeforeClaimStaysQueued() {
        JobRecord queuedOnly = queued("job_d");
        Assert.assertEquals(queuedOnly.getStatus(), JobRecord.Status.QUEUED);
        Assert.assertTrue(queuedOnly.getAttemptId().isBlank());

        JobRecord job = queued("job_e");
        Instant now = Instant.parse("2026-09-17T00:00:00Z");
        DurableJobClaim.Lease lease = DurableJobClaim.tryClaim(job, "wkr_1", now, Duration.ofSeconds(30)).orElseThrow();
        DurableJobClaim.requestCancel(job);
        Assert.assertFalse(DurableJobClaim.mayPublish(job, lease));
        Assert.assertFalse(DurableJobClaim.heartbeat(job, lease, now.plusSeconds(1), Duration.ofSeconds(30)));
    }

    @Test
    public void inputSnapshotDoesNotChangeWhenLaterSettingsChange() {
        JobRecord job = queued("job_f");
        job.setProviderAllowlistSnapshot("ollama");
        String first = DurableJobClaim.hashInputs(job);
        job.setProviderAllowlistSnapshot("cursor");
        String mutated = DurableJobClaim.hashInputs(job);
        Assert.assertNotEquals(first, mutated);
        job.setProviderAllowlistSnapshot("ollama");
        Assert.assertEquals(DurableJobClaim.hashInputs(job), first);
    }

    private static JobRecord queued(String jobId) {
        JobRecord job = new JobRecord(
                jobId, "proj_1", 1L, "NEW", Path.of("in.xlsx"), "https://shop.example", "", "");
        job.setAuthoringEngine(AuthoringEngine.KEEL);
        job.setTenantId("ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        job.setStatus(JobRecord.Status.QUEUED);
        return job;
    }
}
