package delivery.portal.jobs;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class LeaseKeeperTest {

    private static DurableJobClaim.Lease lease(String jobId) {
        return new DurableJobClaim.Lease(jobId, "att-1", "worker-1", java.time.Instant.now().plusSeconds(45), 0,
                DurableJobClaim.Stage.values()[0]);
    }

    @Test
    public void stopsRenewingWhenHeartbeatRefused() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (LeaseKeeper keeper = new LeaseKeeper((id, l) -> calls.incrementAndGet() < 2, Duration.ofHours(1))) {
            keeper.track("j1", lease("j1"));
            Assert.assertEquals(keeper.trackedCount(), 1);
            Thread.sleep(LeaseKeeper.INTERVAL.toMillis() * 2 + 1_000);
            Assert.assertEquals(keeper.trackedCount(), 0, "refused heartbeat must stop renewal");
        }
    }

    @Test
    public void stopsAfterMaxRuntime() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (LeaseKeeper keeper = new LeaseKeeper((id, l) -> {
            calls.incrementAndGet();
            return true;
        }, Duration.ofMillis(1))) {
            keeper.track("j2", lease("j2"));
            Thread.sleep(LeaseKeeper.INTERVAL.toMillis() + 1_000);
            Assert.assertEquals(keeper.trackedCount(), 0, "hung job must let its lease lapse");
            Assert.assertEquals(calls.get(), 0);
        }
    }
}
