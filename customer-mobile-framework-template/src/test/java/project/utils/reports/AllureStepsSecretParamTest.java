package project.utils.reports;

import org.testng.Assert;
import org.testng.annotations.Test;

/** No secret values as Allure parameter payloads — refs only. */
public class AllureStepsSecretParamTest {

    @Test
    public void parameterRedactsSecretLookingKeys() {
        // Must not throw even without Allure on classpath nuances; value path is ref-only
        AllureSteps.parameter("auth.password", "SuperSecretCanary_m403");
        AllureSteps.parameter("phone", "01012345678", true);
        Assert.assertEquals(Secrets.refLabel("auth.password"), "secret-ref:auth.password");
        Assert.assertTrue(Secrets.looksLikeSecretKey("auth.password"));
        Assert.assertFalse(Secrets.looksLikeSecretKey("mobile.udid"));
    }

    @Test
    public void typeSecretStepLabelUsesRefNotValue() {
        String label = Secrets.refLabel("auth.otp");
        Assert.assertTrue(label.startsWith("secret-ref:"));
        Assert.assertFalse(label.contains("123456"));
    }
}
