package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

public class SecretScrubTest {

    @Test
    public void scrubSecretsReplacesJobCredentialsWithPlaceholders() {
        ConversionJobRequest request = request("student", "Password123");
        List<ProvenStep> steps = List.of(
                step("type", "student"),
                step("type", "Password123"),
                step("type", "wrongPassword"),
                step("type", "${TARGET_USERNAME}")
        );
        List<ProvenStep> scrubbed = ProvePhase.scrubSecrets(steps, request);
        Assert.assertEquals(scrubbed.get(0).value(), "${TARGET_USERNAME}");
        Assert.assertEquals(scrubbed.get(1).value(), "${TARGET_PASSWORD}");
        Assert.assertEquals(scrubbed.get(2).value(), "wrongPassword");
        Assert.assertEquals(scrubbed.get(3).value(), "${TARGET_USERNAME}");
    }

    @Test
    public void scrubDraftSecretsCleansLoginAndBodySteps() {
        ConversionJobRequest request = request("student", "SecretPass!");
        TcDraft draft = new TcDraft(
                "TC1", "title", "steps", "expected",
                TcDraftStatus.PASSED,
                List.of(step("type", "SecretPass!")),
                List.of(step("type", "student")),
                true, -1, "", "", "", 0, "https://example.com/login");
        TcDraft clean = ProvePhase.scrubDraftSecrets(draft, request);
        Assert.assertEquals(clean.provenSteps().get(0).value(), "${TARGET_PASSWORD}");
        Assert.assertEquals(clean.loginSteps().get(0).value(), "${TARGET_USERNAME}");
        String irBlob = clean.provenSteps().get(0).value() + clean.loginSteps().get(0).value();
        Assert.assertFalse(irBlob.contains("SecretPass!"));
        Assert.assertFalse(irBlob.contains("student"));
    }

    private static ConversionJobRequest request(String user, String pass) {
        return new ConversionJobRequest(
                "p1", Path.of("x.xlsx"), "https://example.com",
                user, pass, Path.of("work"), Path.of("store"), Path.of("tpl"),
                "NEW", "http://127.0.0.1:11434", "model");
    }

    private static ProvenStep step(String action, String value) {
        return new ProvenStep(
                "TC1", "Login", "action", action,
                "id", "username", value,
                "", "", true, "test", "");
    }
}
