package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class MobileProofInvalidationTest {

    private static MobilePackageFingerprint.Inputs base() {
        return new MobilePackageFingerprint.Inputs(
                "app-1", "apksha", "cred-1", "en-US", "tool-1", "lib-1", "settle-1", "pkg-v1");
    }

    @Test
    public void apkRevisionCredentialLocaleToolchainChangesReprove() throws Exception {
        MobileCaseIr c = load("fixtures/login-case.json");
        String stored = MobilePackageFingerprint.digest(base());
        for (MobilePackageFingerprint.Inputs changed : List.of(
                with(base(), "app-2", null, null, null, null),
                with(base(), null, "apk-NEW", null, null, null),
                with(base(), null, null, "cred-2", null, null),
                with(base(), null, null, null, "ar-EG", null),
                with(base(), null, null, null, null, "tool-2"))) {
            Assert.assertTrue(MobilePackageFingerprint.requiresReprove(stored, changed));
            var decisions = MobileProofInvalidation.decide(
                    List.of(c), Set.of("TC_01"), stored, changed, "app-1");
            Assert.assertEquals(decisions.get(0).decision(), MobileProofInvalidation.Decision.REPROVE,
                    decisions.toString());
        }
    }

    @Test
    public void unselectedOldBuildBecomesStaleTodo() throws Exception {
        MobileCaseIr c = load("fixtures/login-case.json");
        MobilePackageFingerprint.Inputs current = with(base(), "app-2", null, null, null, null);
        var decisions = MobileProofInvalidation.decide(
                List.of(c), Set.of(), MobilePackageFingerprint.digest(base()), current, "app-1");
        Assert.assertEquals(decisions.get(0).decision(), MobileProofInvalidation.Decision.STALE_TODO);
    }

    @Test
    public void noWebReuseShortcutInManifest() {
        var m = MobileProofInvalidation.manifest(base());
        Assert.assertEquals(m.get("reuseShortcut"), "NONE");
        Assert.assertFalse(m.containsKey("ReuseEligibility"));
        Assert.assertTrue(m.get("fingerprint").length() == 64);
    }

    @Test
    public void downloadBoundToExactVersion() {
        Assert.assertTrue(MobileProofInvalidation.downloadBound("pkg-v1", "pkg-v1"));
        Assert.assertFalse(MobileProofInvalidation.downloadBound("pkg-v1", "pkg-v2"));
        Assert.assertFalse(MobileProofInvalidation.downloadBound("", "pkg-v1"));
    }

    @Test
    public void matchingFingerprintKeepsSelectedProvenCase() throws Exception {
        MobileCaseIr c = load("fixtures/login-case.json");
        var inputs = base();
        String fp = MobilePackageFingerprint.digest(inputs);
        var decisions = MobileProofInvalidation.decide(
                List.of(c), Set.of("TC_01"), fp, inputs, "app-1");
        Assert.assertEquals(decisions.get(0).decision(), MobileProofInvalidation.Decision.KEEP);
    }

    private static MobilePackageFingerprint.Inputs with(
            MobilePackageFingerprint.Inputs b, String app, String apk, String cred, String locale, String tool) {
        return new MobilePackageFingerprint.Inputs(
                app != null ? app : b.appRevisionId(),
                apk != null ? apk : b.apkSha256(),
                cred != null ? cred : b.credentialRevisionId(),
                locale != null ? locale : b.locale(),
                tool != null ? tool : b.toolchainLockSha256(),
                b.libraryRevisionId(), b.settleTableSha256(), b.packageVersion());
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileProofInvalidationTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
