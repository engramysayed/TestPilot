package delivery.mobile.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class AuthProfileContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_CANARY = "SECRET_CANARY_OTP_999888";

    private AuthProfileRevision baseRevision(String revId) {
        return new AuthProfileRevision(
                "prof-1", revId, "ten-1", "proj-1",
                "TC-LOGIN", "lib-rev-1", AuthMode.ENSURE_LOGGED_IN,
                List.of("EXISTS:com.example:home"), List.of("EXISTS:com.example:login"),
                "TC-LOGOUT", "",
                new LogicalAccountRef("acct-primary", "Primary tester"),
                List.of(
                        SecretInputDeclaration.password("auth.password"),
                        SecretInputDeclaration.otp("auth.otp"),
                        SecretInputDeclaration.pin("auth.pin")),
                List.of(new ProtectedCredentialRef("cred-pw-1", "auth.password")),
                1_000L);
    }

    @Test
    public void missingOtpAndPinRejectedBeforeJob() {
        AuthProfileRevision pinned = baseRevision("rev-1");
        AuthAdmissionResult missingAll = AuthAdmissionValidator.validateBeforeJob(
                pinned, SuppliedSecretInputs.empty());
        Assert.assertFalse(missingAll.accepted());
        Assert.assertEquals(missingAll.code(), "AUTH_INPUT_MISSING");
        Assert.assertTrue(missingAll.missingKeys().contains("auth.otp"));
        Assert.assertTrue(missingAll.missingKeys().contains("auth.pin"));
        // password is not requiredPerRun by default
        Assert.assertFalse(missingAll.missingKeys().contains("auth.password"));

        AuthAdmissionResult otpOnly = AuthAdmissionValidator.validateBeforeJob(
                pinned, SuppliedSecretInputs.of(Map.of("auth.otp", "123456")));
        Assert.assertFalse(otpOnly.accepted());
        Assert.assertTrue(otpOnly.missingKeys().contains("auth.pin"));

        AuthAdmissionResult ok = AuthAdmissionValidator.validateBeforeJob(
                pinned, SuppliedSecretInputs.of(Map.of(
                        "auth.otp", "123456",
                        "auth.pin", "0000")));
        Assert.assertTrue(ok.accepted());
    }

    @Test
    public void secretCanaryAbsentFromProfileManifestAndLogs() throws Exception {
        AuthProfileRevision pinned = baseRevision("rev-1");
        SuppliedSecretInputs supplied = SuppliedSecretInputs.of(Map.of(
                "auth.otp", SECRET_CANARY,
                "auth.pin", SECRET_CANARY));

        String profileJson = MAPPER.writeValueAsString(pinned);
        Assert.assertFalse(profileJson.contains(SECRET_CANARY), profileJson);
        Assert.assertFalse(pinned.toString().contains(SECRET_CANARY));

        Assert.assertFalse(supplied.toString().contains(SECRET_CANARY));
        String redactedJson = MAPPER.writeValueAsString(supplied.redactedMap());
        Assert.assertFalse(redactedJson.contains(SECRET_CANARY));
        Assert.assertTrue(redactedJson.contains("(redacted)"));

        // values remain available only via explicit execute accessor
        Assert.assertEquals(supplied.getForExecute("auth.otp"), SECRET_CANARY);
    }

    @Test
    public void profileRevisionPinnedAfterLaterEdit() {
        AuthProfileStore store = new AuthProfileStore();
        AuthProfileRevision rev1 = baseRevision("rev-1");
        store.publish(rev1);
        Assert.assertEquals(store.activeRevisionId("prof-1"), "rev-1");

        // Job pins rev-1
        String jobPinnedRevisionId = "rev-1";
        AuthProfileRevision jobPin = store.getRevision(jobPinnedRevisionId).orElseThrow();

        AuthProfileRevision rev2 = rev1.withNewRevision(
                "rev-2",
                2_000L,
                List.of(SecretInputDeclaration.otp("auth.otp")),
                rev1.protectedCredentialRefs(),
                new LogicalAccountRef("acct-primary", "Primary tester (renamed)"));
        store.publishEdit("rev-1", rev2);

        Assert.assertEquals(store.activeRevisionId("prof-1"), "rev-2");
        Assert.assertEquals(store.getActive("prof-1").orElseThrow().account().displayLabel(),
                "Primary tester (renamed)");

        // Pinned job revision unchanged
        AuthProfileRevision stillPinned = store.getRevision(jobPinnedRevisionId).orElseThrow();
        Assert.assertEquals(stillPinned.revisionId(), "rev-1");
        Assert.assertEquals(stillPinned.account().displayLabel(), "Primary tester");
        Assert.assertEquals(stillPinned.inputDeclarations().size(), 3);
        Assert.assertEquals(jobPin.revisionId(), stillPinned.revisionId());
        Assert.assertEquals(jobPin.inputDeclarations().size(), 3);
    }

    @Test
    public void revisionConflictOnStaleEdit() {
        AuthProfileStore store = new AuthProfileStore();
        store.publish(baseRevision("rev-1"));
        store.publishEdit("rev-1", baseRevision("rev-1").withNewRevision(
                "rev-2", 2_000L, List.of(SecretInputDeclaration.otp("auth.otp")), null, null));
        try {
            store.publishEdit("rev-1", baseRevision("rev-1").withNewRevision(
                    "rev-3", 3_000L, List.of(SecretInputDeclaration.otp("auth.otp")), null, null));
            Assert.fail("expected REVISION_CONFLICT");
        } catch (AuthContractException e) {
            Assert.assertEquals(e.code(), "REVISION_CONFLICT");
        }
    }

    @Test
    public void protectedCredentialRefHasNoEmbeddedSecret() throws Exception {
        ProtectedCredentialRef ref = new ProtectedCredentialRef("cred-1", "auth.password");
        String json = MAPPER.writeValueAsString(ref);
        Assert.assertFalse(json.toLowerCase().contains("password="));
        Assert.assertTrue(json.contains("cred-1"));
        Assert.assertFalse(json.contains(SECRET_CANARY));
    }
}
