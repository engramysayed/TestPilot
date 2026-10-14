package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthMode;
import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.LogicalAccountRef;
import delivery.mobile.auth.SecretInputDeclaration;
import delivery.mobile.auth.SuppliedSecretInputs;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class MobileAuthAdmissionTest {

    private static final String CANARY = "SECRET_CANARY_OTP_999888";

    private AuthProfileRevision profile() {
        return new AuthProfileRevision(
                "p", "r1", "t", "pr", "TC-L", "lib",
                AuthMode.FRESH_LOGIN, List.of(), List.of(), "", "",
                new LogicalAccountRef("a1", "A"),
                List.of(SecretInputDeclaration.otp("auth.otp"), SecretInputDeclaration.pin("auth.pin")),
                List.of(), 1L);
    }

    @Test
    public void rejectsMissingBeforeExecute() {
        MobileAuthAdmission admission = new MobileAuthAdmission();
        try {
            admission.assertReady(profile(), SuppliedSecretInputs.empty());
            Assert.fail("expected AuthAdmissionRejectedException");
        } catch (AuthAdmissionRejectedException e) {
            Assert.assertEquals(e.failureCode(), "AUTH_INPUT_MISSING");
            Assert.assertTrue(e.result().missingKeys().contains("auth.otp"));
            Assert.assertFalse(e.getMessage().contains(CANARY));
        }
    }

    @Test
    public void acceptsWhenOtpAndPinSupplied() {
        MobileAuthAdmission admission = new MobileAuthAdmission();
        admission.assertReady(profile(), SuppliedSecretInputs.of(Map.of(
                "auth.otp", CANARY,
                "auth.pin", "1234")));
        Assert.assertFalse(SuppliedSecretInputs.of(Map.of("auth.otp", CANARY)).toString().contains(CANARY));
    }
}
