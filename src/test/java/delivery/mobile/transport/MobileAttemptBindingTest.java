package delivery.mobile.transport;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/** M1-05: stale attempt/device token rejected; cross-tenant 404 shape. */
public class MobileAttemptBindingTest {

    private MobileInputManifest manifest() {
        return new MobileInputManifest("1", "ANDROID", "job_1", "att_1", "proj_1",
                "lib_1", "apprev_1", "devrev_1", "abc", List.of("TC_01"),
                "sec_1", "dev_1", 7L);
    }

    @Test
    public void matchingTupleAccepted() {
        MobileAttemptBinding b = MobileAttemptBinding.fromManifest(manifest());
        b.assertMatches("job_1", "att_1", "dev_1", 7L, manifest().hash());
    }

    @Test
    public void staleAttemptRejected() {
        MobileAttemptBinding b = MobileAttemptBinding.fromManifest(manifest());
        try {
            b.assertMatches("job_1", "att_old", "dev_1", 7L, manifest().hash());
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.STALE_ATTEMPT);
        }
    }

    @Test
    public void staleDeviceFenceRejected() {
        MobileAttemptBinding b = MobileAttemptBinding.fromManifest(manifest());
        try {
            b.assertMatches("job_1", "att_1", "dev_1", 99L, manifest().hash());
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.STALE_DEVICE_LEASE);
        }
    }

    @Test
    public void deviceIdMismatchRejected() {
        MobileAttemptBinding b = MobileAttemptBinding.fromManifest(manifest());
        try {
            b.assertMatches("job_1", "att_1", "dev_other", 7L, manifest().hash());
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.STALE_DEVICE_LEASE);
        }
    }

    @Test
    public void inputHashMismatchRejected() {
        MobileAttemptBinding b = MobileAttemptBinding.fromManifest(manifest());
        try {
            b.assertMatches("job_1", "att_1", "dev_1", 7L, "deadbeef");
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.INPUT_HASH_MISMATCH);
        }
    }

    @Test
    public void crossTenantRegistryReturns404Shape() {
        MobileClaimRegistry registry = new MobileClaimRegistry();
        MobileInputManifest m = manifest();
        MobileClaimSnapshot snap = new MobileClaimSnapshot(
                "tenant_a", m.jobId(), m.attemptId(), "proj_1", m.deviceId(), m.leaseFence(),
                "apprev_1", "devrev_1", "lib_1", "abc", List.of("TC_01"), m.hash(), "sec_1",
                MobileAttemptBinding.fromManifest(m));
        registry.put(snap);
        try {
            registry.requireSameTenant("job_1", "tenant_b");
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.CROSS_TENANT);
        }
    }
}
