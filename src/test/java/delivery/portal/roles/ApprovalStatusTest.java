package delivery.portal.roles;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ApprovalStatusTest {
    @Test
    public void approvedNotActive() {
        Assert.assertEquals(ApprovalStatus.parse("APPROVED"), ApprovalStatus.APPROVED);
        Assert.assertTrue(ApprovalStatus.APPROVED.canStartFeatureJobs());
        Assert.assertFalse(ApprovalStatus.PENDING_APPROVAL.canStartFeatureJobs());
        Assert.assertEquals(ApprovalStatus.LOCKED.denyErrorCode(), "ACCOUNT_LOCKED");
        Assert.assertEquals(ApprovalStatus.PENDING_APPROVAL.denyErrorCode(), "ACCOUNT_NOT_APPROVED");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void activeRejected() {
        ApprovalStatus.parse("ACTIVE");
    }
}
