package delivery.privacy;

import delivery.authoring.LocalLlmClient;
import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SecretSanitizerIntegrationTest {

    private static final String CANARY = "SECRET_CANARY_PASSWORD";

    @Test
    public void htmlSlimmerMasksPasswordValuesBeforeModelContext() {
        String html = "<body><form><input type=\"password\" name=\"p\" value=\"" + CANARY + "\"></form></body>";
        String slim = HtmlSlimmer.slim(html, 80000);
        Assert.assertFalse(slim.contains(CANARY), slim);
        Assert.assertTrue(slim.contains(SecretSanitizer.MASK), slim);
    }

    @Test
    public void huntPackBriefDoesNotRetainCanary() throws Exception {
        Path root = Files.createTempDirectory("hunt-pack-canary");
        delivery.hunt.HuntRequest req = new delivery.hunt.HuntRequest();
        req.setJobId("job_canary");
        req.setProjectId("proj_canary");
        Path zip = delivery.hunt.HuntPackWriter.writePack(
                root, req, "password=" + CANARY, "FINISH", List.of(), List.of(), 1);
        Assert.assertTrue(Files.isRegularFile(zip));
        String brief = Files.readString(root.resolve("brief.md"));
        Assert.assertFalse(brief.contains(CANARY), brief);
    }

    @Test
    public void emptyAllowlistBlocksOllamaHttpDispatch() {
        String prev = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "");
        try {
            LocalLlmClient client = new LocalLlmClient("http://127.0.0.1:9", "unused");
            Assert.assertThrows(ProviderDisallowedException.class, () -> client.completeJson("s", "u"));
        } finally {
            if (prev == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prev);
            }
        }
    }
}
