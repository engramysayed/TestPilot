package delivery.mobile.contracts;

import org.testng.Assert;
import org.testng.annotations.Test;

/** Smoke: module shell loads; no Appium/Android tooling required. */
public class MobileContractsModuleTest {
    @Test
    public void moduleMarkerPresent() {
        Assert.assertEquals(MobileContractsModule.ARTIFACT_ID, "mobile-contracts");
        Assert.assertEquals(MobileContractsModule.SCHEMA_VERSION_PLACEHOLDER, "mobile-ir/1");
    }
}
