package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

public class LocalLlmClientTest {
    @Test
    public void jsonChatRequest_setsHighNumPredictForGenerateBatches() {
        String body = LocalLlmClient.buildChatRequestBody(
                "gemma4:e2b", "sys", "user", true, 8192, new byte[0][]);
        Assert.assertTrue(body.contains("\"num_predict\":8192"), body);
        Assert.assertTrue(body.contains("\"format\":\"json\""), body);
    }

    @Test
    public void chatOutcome_flagsLengthTruncation() {
        Assert.assertTrue(new LocalLlmClient.ChatOutcome("{\"testCases\":[]}", "length").truncated());
        Assert.assertFalse(new LocalLlmClient.ChatOutcome("{\"testCases\":[]}", "stop").truncated());
    }

    @Test
    public void stripThinkingWrappers_removesThinkBlocks() {
        String raw = "<think>scratch</think>\n{\"steps\":[]}";
        Assert.assertEquals(LocalLlmClient.stripThinkingWrappers(raw), "{\"steps\":[]}");
    }

    @Test
    public void stripThinkingWrappers_removesMarkdownFence() {
        String raw = "```json\n{\"steps\":[]}\n```";
        Assert.assertEquals(LocalLlmClient.stripThinkingWrappers(raw), "{\"steps\":[]}");
    }

    @Test
    public void constructor_usesExplicitRequestTimeout() {
        LocalLlmClient client = new LocalLlmClient("http://127.0.0.1:9", "m", Duration.ofSeconds(630));
        Assert.assertEquals(client.requestTimeout(), Duration.ofSeconds(630));
    }

    @Test public void timingPreservesProviderMeasurementsAndMissingValues() {
        var stats = LocalLlmClient.OllamaTiming.from(new org.json.JSONObject()
                .put("total_duration", 9000000000L).put("load_duration", 3000000000L)
                .put("prompt_eval_duration", 2000000000L).put("eval_duration", 4000000000L)
                .put("prompt_eval_count", 200).put("eval_count", 30));
        Assert.assertEquals(stats.loadNs(), 3000000000L);
        Assert.assertEquals(stats.generatedTokens(), 30L);
        Assert.assertEquals(LocalLlmClient.OllamaTiming.from(new org.json.JSONObject()).loadNs(), -1L);
    }
}
