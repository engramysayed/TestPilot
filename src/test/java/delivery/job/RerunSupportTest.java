package delivery.job;

import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;

public class RerunSupportTest {

    @Test
    public void rerunCopiesPinsOntoANewJobWithoutTouchingSourceEvidence() {
        JobRecord source = new JobRecord(
                "job_orig", "prj_1", 1L, "NEW", Path.of("in.xlsx"), "https://staging.example", "", "");
        source.setTenantId("ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        source.setLibraryRevisionId("rev_abc");
        source.setEnvironmentRevisionId("envrev_1");
        source.setProviderAllowlistSnapshot("ollama");
        source.setZipPath(Path.of("out.zip"));
        source.setStatus(JobRecord.Status.FAILED);
        source.setMessage("assert failed");

        JobRecord rerun = RerunSupport.newAttempt(source);
        Assert.assertNotEquals(rerun.getJobId(), source.getJobId());
        Assert.assertEquals(rerun.getParentJobId(), "job_orig");
        Assert.assertEquals(rerun.getLibraryRevisionId(), "rev_abc");
        Assert.assertEquals(rerun.getEnvironmentRevisionId(), "envrev_1");
        Assert.assertEquals(rerun.getProviderAllowlistSnapshot(), "ollama");
        Assert.assertEquals(rerun.getBaseUrl(), "https://staging.example");
        Assert.assertEquals(rerun.getStatus(), JobRecord.Status.QUEUED);
        Assert.assertNull(rerun.getZipPath());
        Assert.assertEquals(source.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(source.getZipPath(), Path.of("out.zip"));
        Assert.assertEquals(rerun.getExcelPath(), Path.of("in.xlsx"));
        Assert.assertEquals(RerunSupport.hashPinnedInputs(rerun), RerunSupport.hashPinnedInputs(source));
    }

    @Test
    public void rerunCanReplaceMissingExcelWithRematerializedPath() {
        JobRecord source = new JobRecord(
                "job_orig", "prj_1", 1L, "EXECUTE", Path.of("gone.xlsx"), "https://staging.example", "", "",
                false, JobRecord.JobKind.EXECUTE);
        source.setLibraryRevisionId("rev_abc");
        JobRecord rerun = RerunSupport.newAttempt(source, Path.of("pinned.xlsx"));
        Assert.assertEquals(rerun.getExcelPath(), Path.of("pinned.xlsx"));
        Assert.assertEquals(source.getExcelPath(), Path.of("gone.xlsx"));
        Assert.assertEquals(rerun.getLibraryRevisionId(), "rev_abc");
    }
}
