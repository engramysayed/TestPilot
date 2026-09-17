package delivery.runner;

import delivery.job.DurableJobClaim;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class PrivateRunnerRulesTest {

    @Test
    public void revokedOrCrossTenantRunnerCannotClaim() {
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        PrivateRunnerRules.Enrollment live = new PrivateRunnerRules.Enrollment(
                "run_1", "ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", now.minusSeconds(10), null, now.minusSeconds(1));
        Assert.assertTrue(PrivateRunnerRules.canClaim(live, live.tenantId(), now, Duration.ofSeconds(45)));
        PrivateRunnerRules.Enrollment revoked = new PrivateRunnerRules.Enrollment(
                "run_1", live.tenantId(), now.minusSeconds(10), now.minusSeconds(1), now.minusSeconds(1));
        Assert.assertFalse(PrivateRunnerRules.canClaim(revoked, live.tenantId(), now, Duration.ofSeconds(45)));
        Assert.assertFalse(PrivateRunnerRules.canClaim(live, "ws_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", now,
                Duration.ofSeconds(45)));
    }

    @Test
    public void offlineBrowserLeaseIsUncertainNotReplayed() {
        JobRecord job = new JobRecord(
                "job_runner", "prj_1", 1L, "NEW", Path.of("in.xlsx"), "https://shop.example", "", "");
        job.setTenantId("ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        job.setStatus(JobRecord.Status.QUEUED);
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        DurableJobClaim.tryClaim(job, "run_1", now, Duration.ofSeconds(10)).orElseThrow();
        DurableJobClaim.markStage(job, job.getAttemptId(), DurableJobClaim.Stage.BROWSER);
        Assert.assertTrue(PrivateRunnerRules.recoverOffline(job, now.plusSeconds(11)));
        Assert.assertEquals(job.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(job.getError(), DurableJobClaim.INTERRUPTED_UNCERTAIN);
        Assert.assertTrue(DurableJobClaim.tryClaim(job, "run_2", now.plusSeconds(12), Duration.ofSeconds(10)).isEmpty());
    }
}
