package delivery.job;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

/** r2: the IR secret flag survives scrub / live resolution and is set for credential values. */
public class SecretFlagTest {
    static ConversionJobRequest req() {
        return new ConversionJobRequest("p1", Path.of("x.xlsx"), "https://example.com", "student", "Password123",
                Path.of("work"), Path.of("store"), Path.of("tpl"), "NEW", "http://127.0.0.1:11434", "model");
    }

    static ProvenStep step(String v) {
        return new ProvenStep("TC1", "Login", "action", "type", "id", "f", v, "", "", true, "test", "");
    }

    @Test public void scrubMarksCredentialValuesSecret() {
        List<ProvenStep> out = ProvePhase.scrubSecrets(List.of(step("student"), step("Password123"), step("${OTP_CODE}"),
                step("hello"), step("x").withSecret(true)), req());
        Assert.assertFalse(out.get(0).secret(), "username is not a secret");
        Assert.assertTrue(out.get(1).secret());
        Assert.assertEquals(out.get(1).value(), "${TARGET_PASSWORD}");
        Assert.assertTrue(out.get(2).secret());
        Assert.assertFalse(out.get(3).secret());
        Assert.assertTrue(out.get(4).secret(), "target-detected flag is preserved");
    }

    @Test public void liveResolutionKeepsFlag() {
        List<ProvenStep> live = LoginSecretResolver.resolveForLive(List.of(step("${TARGET_PASSWORD}")), req());
        Assert.assertEquals(live.get(0).value(), "Password123");
        Assert.assertTrue(live.get(0).secret());
        Assert.assertFalse(live.get(0).toString().contains("Password123"));
    }

    @Test public void copySitesKeepCodegenIrFields() {
        var cand = java.util.List.of(new delivery.codegen.LocatorCandidate("keel", "css=#a", 0.9));
        ProvenStep s = new ProvenStep("TC1", "Login", "action", "type", "id", "f", "${TARGET_PASSWORD}", "", "", true, "t", "",
                cand, java.util.List.of(), "none", false);
        ProvenStep live = LoginSecretResolver.resolveForLive(List.of(s), req()).get(0);
        ProvenStep scrub = ProvePhase.scrubSecrets(List.of(live), req()).get(0);
        for (ProvenStep p : List.of(live, scrub)) {
            Assert.assertEquals(p.locatorCandidates(), cand);
            Assert.assertEquals(p.sideEffects(), "none");
            Assert.assertTrue(p.secret());
        }
    }
}
