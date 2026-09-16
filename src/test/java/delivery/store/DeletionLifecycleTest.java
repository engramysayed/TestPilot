package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DeletionLifecycleTest {

    @Test
    public void tombstoneRejectsNewWorkAndStalePublish() {
        DeletionLifecycle.State live = DeletionLifecycle.live();
        Assert.assertTrue(live.mayAdmit());
        Assert.assertTrue(live.mayPublish("att_1"));
        DeletionLifecycle.State tomb = live.tombstone();
        Assert.assertFalse(tomb.mayAdmit());
        Assert.assertFalse(tomb.mayPublish("att_1"));
        Assert.assertEquals(tomb.phase(), DeletionLifecycle.Phase.TOMBSTONED);
    }

    @Test
    public void purgeWaitsForWorkerAckThenIsRetryable() {
        DeletionLifecycle.State tomb = DeletionLifecycle.live().tombstone();
        Assert.assertFalse(tomb.canPurge(true), "active lease must block purge");
        DeletionLifecycle.State ready = tomb.acknowledgeIdle();
        Assert.assertTrue(ready.canPurge(false));
        DeletionLifecycle.State failed = ready.purgeFailed("disk busy");
        Assert.assertEquals(failed.phase(), DeletionLifecycle.Phase.PURGE_FAILED);
        Assert.assertTrue(failed.canPurge(false), "partial cleanup must be retryable");
        Assert.assertTrue(failed.lastError().contains("disk busy"));
        Assert.assertEquals(failed.purge().phase(), DeletionLifecycle.Phase.PURGED);
        Assert.assertFalse(failed.purge().mayPublish("att_stale"));
    }
}
