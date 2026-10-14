package delivery.mobile.transport;

import delivery.mobile.model.ProjectPlatform;
import keel.contract.runner.RunnerCapability;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.List;

/** M1-05: web-only runner cannot claim mobile; flag gate. */
public class MobileClaimGateTest {

    @Test
    public void webOnlyRunnerCannotClaimAndroid() {
        Assert.assertFalse(MobileClaimGate.canClaim(
                List.of(RunnerCapability.BROWSER_CHROME), ProjectPlatform.ANDROID, true));
        try {
            MobileClaimGate.requireCanClaim(List.of(RunnerCapability.BROWSER_CHROME),
                    ProjectPlatform.ANDROID, true);
            Assert.fail("expected capability missing");
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.RUNNER_CAPABILITY_MISSING);
        }
    }

    @Test
    public void omittedCapabilitiesAreWebOnly() {
        Assert.assertTrue(MobileClaimGate.canClaim(null, ProjectPlatform.WEB, false));
        Assert.assertFalse(MobileClaimGate.canClaim(null, ProjectPlatform.ANDROID, true));
        Assert.assertEquals(MobileClaimGate.normalize(null),
                EnumSet.of(RunnerCapability.BROWSER_CHROME));
    }

    @Test
    public void androidCapabilityClaimsWhenEnabled() {
        Assert.assertTrue(MobileClaimGate.canClaim(
                List.of(RunnerCapability.ANDROID_UIAUTOMATOR2, RunnerCapability.BROWSER_CHROME),
                ProjectPlatform.ANDROID, true));
    }

    @Test
    public void androidClaimRejectedWhenFlagOff() {
        Assert.assertFalse(MobileClaimGate.canClaim(
                List.of(RunnerCapability.ANDROID_UIAUTOMATOR2), ProjectPlatform.ANDROID, false));
        try {
            MobileClaimGate.requireCanClaim(List.of(RunnerCapability.ANDROID_UIAUTOMATOR2),
                    ProjectPlatform.ANDROID, false);
            Assert.fail("expected disabled");
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.MOBILE_DISABLED);
        }
    }
}
