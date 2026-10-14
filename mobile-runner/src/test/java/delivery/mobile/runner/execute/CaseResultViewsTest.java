package delivery.mobile.runner.execute;

import delivery.mobile.result.CaseOutcomeView;
import delivery.mobile.result.FailureEvidenceManifest;
import delivery.mobile.result.MobileCaseResultView;
import delivery.mobile.result.ObservedAppIdentity;
import delivery.mobile.result.ObservedDeviceIdentity;
import delivery.mobile.result.AppIdentitySource;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CaseResultViewsTest {

    @Test
    public void mapsPassedFailedBlockedInterrupted() {
        Assert.assertEquals(
                CaseResultViews.toView(CaseResult.passed("TC-A")).outcome(),
                CaseOutcomeView.PASSED);
        FailureEvidenceManifest ev = new FailureEvidenceManifest(
                "s1", "X", false, 5000, 10, "", "", List.of(), "", false, false, false);
        MobileCaseResultView failed = CaseResultViews.toView(CaseResult.failed("TC-B", "ASSERT", ev));
        Assert.assertEquals(failed.outcome(), CaseOutcomeView.FAILED);
        Assert.assertEquals(failed.failureCode(), "ASSERT");
        Assert.assertNotNull(failed.evidence());

        Assert.assertEquals(
                CaseResultViews.toView(CaseResult.blocked("TC-C", "SETUP")).outcome(),
                CaseOutcomeView.BLOCKED);
        Assert.assertEquals(
                CaseResultViews.toView(CaseResult.interrupted("TC-D")).outcome(),
                CaseOutcomeView.INTERRUPTED_UNCERTAIN);
    }

    @Test
    public void enrichesIdentitiesWithoutRawSerial() {
        ObservedAppIdentity app = new ObservedAppIdentity(
                "com.ex", 1L, "1.0", ".A", "", AppIdentitySource.INSTALLED);
        ObservedDeviceIdentity device = ObservedDeviceIdentity.ofHashed(
                "d1", "model", 34, "arm64-v8a", "emulator-5554");
        MobileCaseResultView view = CaseResultViews.toView(
                CaseResult.passed("TC-E"), "att-9", app, device);
        Assert.assertEquals(view.attemptId(), "att-9");
        Assert.assertEquals(view.observedApp().packageName(), "com.ex");
        Assert.assertFalse(view.observedDevice().serialFingerprint().contains("emulator"));
        Assert.assertFalse(view.containsRawSecret());
    }

    @Test
    public void nullResultBecomesBlocked() {
        MobileCaseResultView view = CaseResultViews.toView(null);
        Assert.assertEquals(view.outcome(), CaseOutcomeView.BLOCKED);
        Assert.assertEquals(view.failureCode(), "MISSING_RESULT");
    }
}
