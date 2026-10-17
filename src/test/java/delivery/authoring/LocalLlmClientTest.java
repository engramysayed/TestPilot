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

    @Test
    public void ollamaAuthorizationHeader_bearerWhenKeySet() {
        String previous = System.getProperty("OLLAMA_API_KEY");
        // If ambient env already has OLLAMA_API_KEY, header still non-blank; assert shape only.
        System.setProperty("OLLAMA_API_KEY", "ollama-unit-test-key");
        try {
            String header = LocalLlmClient.ollamaAuthorizationHeader();
            if (System.getenv("OLLAMA_API_KEY") != null && !System.getenv("OLLAMA_API_KEY").isBlank()) {
                Assert.assertTrue(header != null && header.startsWith("Bearer "));
            } else {
                Assert.assertEquals(header, "Bearer ollama-unit-test-key");
            }
        } finally {
            if (previous == null) {
                System.clearProperty("OLLAMA_API_KEY");
            } else {
                System.setProperty("OLLAMA_API_KEY", previous);
            }
        }
    }

    @Test
    public void ollamaAuthorizationHeader_absentWhenKeyBlank() {
        if (System.getenv("OLLAMA_API_KEY") != null && !System.getenv("OLLAMA_API_KEY").isBlank()) {
            return; // env wins; cannot assert blank in this JVM
        }
        String previous = System.getProperty("OLLAMA_API_KEY");
        System.clearProperty("OLLAMA_API_KEY");
        try {
            Assert.assertNull(LocalLlmClient.ollamaAuthorizationHeader());
        } finally {
            if (previous == null) {
                System.clearProperty("OLLAMA_API_KEY");
            } else {
                System.setProperty("OLLAMA_API_KEY", previous);
            }
        }
    }

    @Test
    public void completeChat_sendsBearerWhenKeySet_absentWhenBlank() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> seenAuth =
                new java.util.concurrent.atomic.AtomicReference<>();
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"message\":{\"content\":\"{\\\"ok\\\":true}\"},\"done_reason\":\"stop\"}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String prevKey = System.getProperty("OLLAMA_API_KEY");
        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "ollama");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            if (System.getenv("OLLAMA_API_KEY") == null || System.getenv("OLLAMA_API_KEY").isBlank()) {
                System.clearProperty("OLLAMA_API_KEY");
                LocalLlmClient noKey = new LocalLlmClient(base, "m", java.time.Duration.ofSeconds(5));
                noKey.completeJson("s", "u");
                Assert.assertNull(seenAuth.get());

                seenAuth.set(null);
                System.setProperty("OLLAMA_API_KEY", "ollama-unit-bearer");
                LocalLlmClient withKey = new LocalLlmClient(base, "m", java.time.Duration.ofSeconds(5));
                withKey.completeJson("s", "u");
                Assert.assertEquals(seenAuth.get(), "Bearer ollama-unit-bearer");
            } else {
                // Env present: only assert that a Bearer header is sent.
                LocalLlmClient withEnv = new LocalLlmClient(base, "m", java.time.Duration.ofSeconds(5));
                withEnv.completeJson("s", "u");
                Assert.assertTrue(seenAuth.get() != null && seenAuth.get().startsWith("Bearer "));
            }
        } finally {
            if (prevKey == null) {
                System.clearProperty("OLLAMA_API_KEY");
            } else {
                System.setProperty("OLLAMA_API_KEY", prevKey);
            }
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
            server.stop(0);
        }
    }
}
