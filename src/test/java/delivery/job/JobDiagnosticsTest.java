package delivery.job;

import delivery.ir.TcDraftStatus;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public class JobDiagnosticsTest {

    @Test
    public void queuedAndCancellingExposeNextActions() {
        JobRecord queued = job(JobRecord.Status.QUEUED, "", "");
        Map<String, Object> q = JobDiagnostics.describe(queued, false, false);
        Assert.assertEquals(q.get("proofKind"), "UNCHECKED");
        Assert.assertTrue(String.valueOf(q.get("nextAction")).toLowerCase().contains("wait"));

        JobRecord cancelling = job(JobRecord.Status.CANCELLING, "Cancellation requested", "");
        Map<String, Object> c = JobDiagnostics.describe(cancelling, false, false);
        Assert.assertEquals(c.get("status"), "CANCELLING");
        Assert.assertTrue(String.valueOf(c.get("nextAction")).toLowerCase().contains("acknowledg"));
    }

    @Test
    public void interruptedUncertainWarnsAgainstBlindReplay() {
        JobRecord job = job(JobRecord.Status.FAILED, "lease expired", DurableJobClaim.INTERRUPTED_UNCERTAIN);
        Map<String, Object> d = JobDiagnostics.describe(job, false, false);
        Assert.assertEquals(d.get("proofKind"), "INTERRUPTED");
        Assert.assertTrue(String.valueOf(d.get("nextAction")).toLowerCase().contains("review"));
    }

    @Test
    public void simulatedDryRunIsDistinctFromFreshProof() {
        JobRecord job = job(JobRecord.Status.COMPLETED, "dry-run completed", "");
        Map<String, Object> d = JobDiagnostics.describe(job, true, false);
        Assert.assertEquals(d.get("proofKind"), "SIMULATED");
        Assert.assertEquals(d.get("downloadAvailable"), Boolean.TRUE);
    }

    @Test
    public void reusedProofIsDistinctFromFreshAndExpiredDownloadIsVisible() {
        JobRecord job = job(JobRecord.Status.COMPLETED, "reused prior proof", "");
        job.setPassedCount(2);
        Map<String, Object> reused = JobDiagnostics.describe(job, true, false);
        Assert.assertEquals(reused.get("proofKind"), "REUSED");

        JobRecord fresh = job(JobRecord.Status.COMPLETED, "proven on live browser", "");
        fresh.setPassedCount(2);
        Map<String, Object> f = JobDiagnostics.describe(fresh, false, true);
        Assert.assertEquals(f.get("proofKind"), "FRESH");
        Assert.assertEquals(f.get("downloadAvailable"), Boolean.FALSE);
        Assert.assertTrue(String.valueOf(f.get("nextAction")).toLowerCase().contains("expired")
                || String.valueOf(f.get("nextAction")).toLowerCase().contains("missing"));
    }

    @Test
    public void blockedAndUncheckedStayOutOfPassInflation() {
        JobRecord blocked = job(JobRecord.Status.COMPLETED_WITH_BLOCK, "soft block", "");
        Assert.assertEquals(JobDiagnostics.describe(blocked, true, false).get("proofKind"), "BLOCKED");

        JobRecord unchecked = job(JobRecord.Status.COMPLETED, "all TODO", "");
        unchecked.setTodoCount(3);
        Assert.assertEquals(JobDiagnostics.describe(unchecked, true, false).get("proofKind"), "UNCHECKED");
    }

    @Test
    public void caseProofKindMapsDraftStatus() {
        Assert.assertEquals(JobDiagnostics.caseProofKind(TcDraftStatus.PASSED, false), "FRESH");
        Assert.assertEquals(JobDiagnostics.caseProofKind(TcDraftStatus.REUSED, false), "REUSED");
        Assert.assertEquals(JobDiagnostics.caseProofKind(TcDraftStatus.PARTIAL, false), "BLOCKED");
        Assert.assertEquals(JobDiagnostics.caseProofKind(TcDraftStatus.TODO, true), "SIMULATED");
        Assert.assertEquals(JobDiagnostics.caseProofKind(TcDraftStatus.TODO, false), "UNCHECKED");
    }

    private static JobRecord job(JobRecord.Status status, String message, String error) {
        JobRecord job = new JobRecord("job_d", "prj_d", 1L, "NEW", Path.of("x.xlsx"),
                "https://example.com", "", "");
        job.setStatus(status);
        job.setMessage(message);
        job.setError(error);
        job.setCreatedAt(Instant.parse("2026-09-17T00:00:00Z"));
        job.setLibraryRevisionId("rev_abc");
        job.setProviderAllowlistSnapshot("ollama");
        return job;
    }
}
