package delivery.mobile.runner;

import delivery.mobile.contracts.MobileContractsModule;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Smoke: runner shell + contracts dependency; does not launch Appium/device. */
public class MobileRunnerModuleTest {
    @Test
    public void modulePinsMatchProposedToolchain() {
        Assert.assertEquals(MobileRunnerModule.ARTIFACT_ID, "mobile-runner");
        Assert.assertEquals(MobileRunnerModule.PROPOSED_APPIUM_JAVA_CLIENT, "10.1.1");
        Assert.assertEquals(MobileRunnerModule.PROPOSED_SELENIUM, "4.43.0");
        Assert.assertEquals(MobileContractsModule.ARTIFACT_ID, "mobile-contracts");
    }
}
