package delivery.mobile.lease;

import delivery.mobile.MobilePortalStore;
import delivery.mobile.MobileProperties;
import delivery.mobile.model.MobileDeviceBusy;
import delivery.mobile.model.MobileDeviceRecord;
import delivery.mobile.persistence.MobileDeviceEntity;
import delivery.mobile.persistence.MobileDeviceLeaseRepository;
import delivery.mobile.persistence.MobileDeviceRepository;
import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M1-04: host-scoped lock + durable lease/fence + quarantine reconciliation.
 * Required coverage: two concurrent jobs/tenants/agent processes, late heartbeat,
 * expired lease with active old session, no release without cleanup acknowledgment.
 */
@SpringBootTest(classes = {PortalApplication.class, DeviceLeaseGuardTest.LeaseTestConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m104;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-mobile-m104",
        "delivery.work-dir=./target/test-delivery-work-mobile-m104",
        "delivery.mobile.enabled=false",
        "delivery.mobile.live-tests=false",
        "delivery.mobile.lease-heartbeat-seconds=5",
        "delivery.mobile.lease-expiry-seconds=30"
})
public class DeviceLeaseGuardTest extends AbstractTestNGSpringContextTests {

    @TestConfiguration
    static class LeaseTestConfig {
        static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-14T12:00:00Z"));

        @Bean
        @Primary
        ClockHolder clockHolder() {
            return new ClockHolder(CLOCK);
        }

        @Bean
        @Primary
        java.time.Clock clock(ClockHolder holder) {
            return holder.clock;
        }
    }

    /** Indirection so MutableClock advances are visible through the Clock bean. */
    static final class ClockHolder {
        final MutableClock clock;
        ClockHolder(MutableClock clock) { this.clock = clock; }
    }

    @Autowired DeviceLeaseGuard guard;
    @Autowired MobilePortalStore mobileStore;
    @Autowired MobileDeviceRepository deviceRepository;
    @Autowired MobileDeviceLeaseRepository leaseRepository;
    @Autowired MobileProperties mobileProperties;
    @Autowired ClockHolder clockHolder;

    @BeforeMethod
    public void resetClockAndLockRoot() throws Exception {
        clockHolder.clock.set(Instant.parse("2026-10-14T12:00:00Z"));
        var root = Files.createTempDirectory("m104-host-locks");
        mobileProperties.setHostLockRoot(root.toAbsolutePath().toString());
        mobileProperties.setLeaseExpirySeconds(30);
        mobileProperties.setLeaseHeartbeatSeconds(5);
    }

    private MobileDeviceRecord register(String tenant, String host, String fp) {
        return mobileStore.registerDevice(tenant, host, fp, fp.substring(0, Math.min(8, fp.length())),
                "Phone-" + fp, "Pixel", 34, "arm64-v8a");
    }

    @Test
    public void twoConcurrentJobsSameDeviceOneWins() throws Exception {
        MobileDeviceRecord device = register("tenant_a", "host_concurrent", "fp_concurrent_1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger busy = new AtomicInteger();
        List<LeaseHandle> handles = new ArrayList<>();
        Object lock = new Object();

        Callable<Void> attempt = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                LeaseHandle h = guard.reserve("tenant_a", device.getDeviceId(),
                        "job_" + UUID.randomUUID(), "att_" + UUID.randomUUID(), "hash_a");
                wins.incrementAndGet();
                synchronized (lock) {
                    handles.add(h);
                }
            } catch (DeviceLeaseException e) {
                Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.DEVICE_BUSY);
                busy.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(attempt);
        Future<Void> f2 = pool.submit(attempt);
        Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        Assert.assertEquals(wins.get(), 1, "exactly one job may reserve");
        Assert.assertEquals(busy.get(), 1, "loser sees DEVICE_BUSY");
        Assert.assertEquals(handles.size(), 1);
        MobileLeaseRecord rec = handles.get(0).record();
        Assert.assertEquals(rec.getState(), LeaseState.RESERVED.name());
        Assert.assertEquals(rec.getInputHash(), "hash_a");
        Assert.assertTrue(rec.getFenceToken() >= 1);

        // cleanup
        guard.beginRelease(rec.getLeaseId(), rec.getFenceToken());
        guard.acknowledgeCleanup(rec.getLeaseId(), rec.getFenceToken());
    }

    @Test
    public void twoTenantsCannotSplitPhysicalHostLock() throws Exception {
        // Physical binding is unique on (hostId, serialFingerprint) — second tenant registration rejected.
        register("tenant_x", "host_shared", "fp_shared_phone");
        try {
            register("tenant_y", "host_shared", "fp_shared_phone");
            Assert.fail("expected tenant foreign reference rejection for same physical binding");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("tenant foreign reference"));
        }

        // Two agent processes: host lock contention on same physical key under different job attempts
        // (same tenant device) covered by HostDeviceLockTest + concurrent reserve above.
        PathProbe:
        {
            var root = java.nio.file.Path.of(mobileProperties.getHostLockRoot());
            var lockPath = HostDeviceLock.lockPath(root, "host_shared", "fp_shared_phone");
            Assert.assertFalse(lockPath.toString().contains("tenant_x"));
            Assert.assertFalse(lockPath.toString().contains("tenant_y"));
        }
    }

    @Test
    public void lateHeartbeatRejectedAndCommandsStoppedButDeviceNotReleased() {
        MobileDeviceRecord device = register("tenant_hb", "host_hb", "fp_hb_1");
        LeaseHandle handle = guard.reserve("tenant_hb", device.getDeviceId(), "job_hb", "att_hb", "in_hb");
        String leaseId = handle.leaseId();
        long fence = handle.fenceToken();

        guard.activate(leaseId, fence, "session-old", "pid-old");
        Assert.assertTrue(guard.allowsCommands(leaseId, fence));

        MobileLeaseRecord ok = guard.heartbeat(leaseId, fence);
        Assert.assertTrue(ok.isCommandsAllowed());

        clockHolder.clock.advance(Duration.ofSeconds(31));
        Assert.assertFalse(guard.allowsCommands(leaseId, fence), "expiry stops new commands");

        try {
            guard.heartbeat(leaseId, fence);
            Assert.fail("late heartbeat must be rejected");
        } catch (DeviceLeaseException e) {
            Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.EXPIRED_COMMANDS);
        }

        // Durable ownership remains — second job cannot reserve
        try {
            guard.reserve("tenant_hb", device.getDeviceId(), "job_hb2", "att_hb2", "in_hb2");
            Assert.fail("expired lease must still block reserve");
        } catch (DeviceLeaseException e) {
            Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.DEVICE_BUSY);
        }

        MobileDeviceEntity entity = deviceRepository.findByDeviceId(device.getDeviceId()).orElseThrow();
        Assert.assertEquals(entity.getBusy(), MobileDeviceBusy.ACTIVE.name());
        Assert.assertTrue(leaseRepository.findByDeviceId(device.getDeviceId()).isPresent());
    }

    @Test
    public void expiredLeaseWithActiveOldSessionQuarantinesWithoutRelease() {
        MobileDeviceRecord device = register("tenant_exp", "host_exp", "fp_exp_1");
        LeaseHandle handle = guard.reserve("tenant_exp", device.getDeviceId(), "job_exp", "att_exp", "hash_exp");
        String leaseId = handle.leaseId();
        long fence = handle.fenceToken();
        guard.activate(leaseId, fence, "session-still-alive", "worker-pid-9");

        clockHolder.clock.advance(Duration.ofSeconds(60));
        MobileLeaseRecord quarantined = guard.reconcile(leaseId, null);
        Assert.assertNotNull(quarantined);
        Assert.assertEquals(quarantined.getState(), LeaseState.QUARANTINED.name());
        Assert.assertEquals(quarantined.getCleanupStatus(), LeaseCleanupStatus.UNCERTAIN.name());
        Assert.assertEquals(quarantined.getSessionRef(), "session-still-alive");

        MobileDeviceEntity entity = deviceRepository.findByDeviceId(device.getDeviceId()).orElseThrow();
        Assert.assertEquals(entity.getBusy(), MobileDeviceBusy.QUARANTINED.name());

        // Still no free release without proof
        try {
            guard.reconcile(leaseId, new DeviceLeaseGuard.QuarantineProof(false, true));
            Assert.fail("incomplete proof must not free quarantined device");
        } catch (DeviceLeaseException e) {
            Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.QUARANTINE_PROOF_REQUIRED);
        }

        // With proof, device returns to AVAILABLE
        Assert.assertNull(guard.reconcile(leaseId, DeviceLeaseGuard.QuarantineProof.cleared()));
        entity = deviceRepository.findByDeviceId(device.getDeviceId()).orElseThrow();
        Assert.assertEquals(entity.getBusy(), MobileDeviceBusy.AVAILABLE.name());
        Assert.assertTrue(leaseRepository.findByDeviceId(device.getDeviceId()).isEmpty());
    }

    @Test
    public void noReleaseWithoutCleanupAcknowledgment() {
        MobileDeviceRecord device = register("tenant_ack", "host_ack", "fp_ack_1");
        LeaseHandle handle = guard.reserve("tenant_ack", device.getDeviceId(), "job_ack", "att_ack", "hash_ack");
        String leaseId = handle.leaseId();
        long fence = handle.fenceToken();
        guard.activate(leaseId, fence, "sess", "proc");

        try {
            guard.releaseWithoutCleanupRejected(leaseId, fence);
            Assert.fail("expected CLEANUP_REQUIRED");
        } catch (DeviceLeaseException e) {
            Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.CLEANUP_REQUIRED);
            Assert.assertTrue(e.getMessage().contains("cleanup acknowledgment"));
        }

        // Still ACTIVE / leased
        Assert.assertEquals(guard.getLease(leaseId).getState(), LeaseState.ACTIVE.name());
        Assert.assertTrue(leaseRepository.findByDeviceId(device.getDeviceId()).isPresent());

        try {
            guard.acknowledgeCleanup(leaseId, fence);
            Assert.fail("ACK while ACTIVE must fail — must beginRelease first");
        } catch (DeviceLeaseException e) {
            Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.INVALID_STATE);
        }

        guard.beginRelease(leaseId, fence);
        Assert.assertEquals(guard.getLease(leaseId).getState(), LeaseState.RELEASING.name());
        Assert.assertEquals(guard.getLease(leaseId).getCleanupStatus(), LeaseCleanupStatus.PENDING.name());

        guard.acknowledgeCleanup(leaseId, fence);
        MobileDeviceEntity entity = deviceRepository.findByDeviceId(device.getDeviceId()).orElseThrow();
        Assert.assertEquals(entity.getBusy(), MobileDeviceBusy.AVAILABLE.name());
        Assert.assertTrue(leaseRepository.findByDeviceId(device.getDeviceId()).isEmpty());
    }

    @Test
    public void admissionIncrementsFenceTokenAndBindsAttemptInputHash() {
        MobileDeviceRecord device = register("tenant_fence", "host_fence", "fp_fence_1");
        LeaseHandle h1 = guard.reserve("tenant_fence", device.getDeviceId(), "job1", "att1", "hash1");
        long fence1 = h1.fenceToken();
        Assert.assertEquals(fence1, 1L);
        Assert.assertEquals(h1.record().getAttemptId(), "att1");
        Assert.assertEquals(h1.record().getInputHash(), "hash1");

        guard.beginRelease(h1.leaseId(), fence1);
        guard.acknowledgeCleanup(h1.leaseId(), fence1);

        LeaseHandle h2 = guard.reserve("tenant_fence", device.getDeviceId(), "job2", "att2", "hash2");
        Assert.assertEquals(h2.fenceToken(), 2L, "monotonic fence across admissions");
        Assert.assertEquals(h2.record().getInputHash(), "hash2");

        try {
            guard.heartbeat(h2.leaseId(), fence1);
            Assert.fail("stale fence must be rejected");
        } catch (DeviceLeaseException e) {
            Assert.assertEquals(e.getCode(), DeviceLeaseErrorCode.FENCE_MISMATCH);
        }

        guard.beginRelease(h2.leaseId(), h2.fenceToken());
        guard.acknowledgeCleanup(h2.leaseId(), h2.fenceToken());
    }

    @Test
    public void createLeaseDelegatesAndPreservesBusySemantics() {
        MobileDeviceRecord device = register("tenant_legacy", "host_legacy", "fp_legacy_1");
        var lease = mobileStore.createLease("tenant_legacy", device.getDeviceId(), "job_l", "att_l");
        Assert.assertEquals(lease.getState(), "RESERVED");
        Assert.assertTrue(lease.getFenceToken() >= 1);
        try {
            mobileStore.createLease("tenant_legacy", device.getDeviceId(), "job_l2", "att_l2");
            Assert.fail("expected DEVICE_BUSY");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("DEVICE_BUSY"));
        }
        guard.beginRelease(lease.getLeaseId(), lease.getFenceToken());
        guard.acknowledgeCleanup(lease.getLeaseId(), lease.getFenceToken());
    }
}
