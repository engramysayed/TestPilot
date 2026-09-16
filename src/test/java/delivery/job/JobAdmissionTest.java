package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JobAdmissionTest {

    @Test
    public void saturatesTenantQueueWithoutCreatingAQueuedRecord() {
        JobAdmission.Limits limits = new JobAdmission.Limits(2, 8);
        Assert.assertTrue(JobAdmission.inspect(1, 0, limits).admitted());
        JobAdmission.Decision saturated = JobAdmission.inspect(2, 0, limits);
        Assert.assertFalse(saturated.admitted());
        Assert.assertEquals(saturated.code(), "QUEUE_SATURATED");
        Assert.assertFalse(saturated.reason().toLowerCase().contains("secret"));
    }

    @Test
    public void saturatesInstallRunningSlots() {
        JobAdmission.Limits limits = new JobAdmission.Limits(20, 1);
        Assert.assertFalse(JobAdmission.inspect(0, 1, limits).admitted());
        Assert.assertEquals(JobAdmission.inspect(0, 1, limits).code(), "INSTALL_BUSY");
    }

    @Test
    public void fromEnvironmentFailsClosedOnZeroLimits() {
        JobAdmission.Limits limits = new JobAdmission.Limits(0, 0);
        Assert.assertFalse(JobAdmission.inspect(0, 0, limits).admitted());
    }
}
