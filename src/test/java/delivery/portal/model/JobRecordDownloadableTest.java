package delivery.portal.model;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JobRecordDownloadableTest {

    @Test
    public void executeCompleted_isNotDownloadable() {
        Assert.assertFalse(JobRecord.isDownloadable(JobRecord.JobKind.EXECUTE, JobRecord.Status.COMPLETED));
    }

    @Test
    public void convertCompleted_isDownloadable() {
        Assert.assertTrue(JobRecord.isDownloadable(JobRecord.JobKind.CONVERT, JobRecord.Status.COMPLETED));
    }

    @Test
    public void convertCompletedWithBlock_isDownloadable() {
        Assert.assertTrue(JobRecord.isDownloadable(
                JobRecord.JobKind.CONVERT, JobRecord.Status.COMPLETED_WITH_BLOCK));
    }

    @Test
    public void executeCompletedString_isNotDownloadable() {
        Assert.assertFalse(JobRecord.isDownloadable(JobRecord.JobKind.EXECUTE, "COMPLETED"));
    }

    @Test
    public void parseJobKind_nullDefaultsToConvert() {
        Assert.assertEquals(JobRecord.parseJobKind(null), JobRecord.JobKind.CONVERT);
    }

    @Test
    public void generateBatchCompleted_isDownloadable() {
        Assert.assertTrue(JobRecord.isDownloadable(
                JobRecord.JobKind.GENERATE_BATCH, JobRecord.Status.COMPLETED));
    }

    @Test
    public void generateBatchCompletedWithBlockString_isNotDownloadable() {
        Assert.assertFalse(JobRecord.isDownloadable(
                JobRecord.JobKind.GENERATE_BATCH, "COMPLETED_WITH_BLOCK"));
    }

    @Test
    public void generateCompareCompleted_isNotDownloadable() {
        Assert.assertFalse(JobRecord.isDownloadable(
                JobRecord.JobKind.GENERATE_COMPARE, JobRecord.Status.COMPLETED));
    }

    @Test
    public void generateBatchRunning_isNotDownloadable() {
        Assert.assertFalse(JobRecord.isDownloadable(
                JobRecord.JobKind.GENERATE_BATCH, JobRecord.Status.RUNNING));
    }
}
