package delivery.portal.model;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.time.Instant;

public class JobRecordRunAtTest {

    @Test
    public void runAtPrefersCompletedAtOverCreatedAt() {
        JobRecord job = new JobRecord(
                "job_1", "prj_1", 1L, "HEADLESS", Path.of("x.xlsx"),
                "https://example.com", "", "", false, JobRecord.JobKind.EXECUTE);
        Instant created = Instant.parse("2026-08-25T10:00:00Z");
        Instant completed = Instant.parse("2026-08-25T10:30:00Z");
        job.setCreatedAt(created);
        job.setCompletedAt(completed);
        Assert.assertEquals(job.runAt(), completed);
    }

    @Test
    public void runAtFallsBackToCreatedAt() {
        JobRecord job = new JobRecord(
                "job_2", "prj_1", 1L, "HEADLESS", Path.of("x.xlsx"),
                "https://example.com", "", "", false, JobRecord.JobKind.EXECUTE);
        Instant created = Instant.parse("2026-08-25T10:00:00Z");
        job.setCreatedAt(created);
        Assert.assertEquals(job.runAt(), created);
    }

    @Test
    public void runAtNullWhenUnset() {
        JobRecord job = new JobRecord(
                "job_3", "prj_1", 1L, "HEADLESS", Path.of("x.xlsx"),
                "https://example.com", "", "", false, JobRecord.JobKind.EXECUTE);
        Assert.assertNull(job.runAt());
    }

    @Test
    public void workingForLabel_usesCreatedAtNotPageLoad() {
        Instant created = Instant.parse("2026-08-31T10:00:00Z");
        Instant now = Instant.parse("2026-08-31T10:01:05Z");
        Assert.assertEquals(JobRecord.workingForLabel(created, now), "Working for 1:05");
    }
}
