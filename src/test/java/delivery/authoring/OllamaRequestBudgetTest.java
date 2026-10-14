package delivery.authoring;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

public class OllamaRequestBudgetTest {
    private HttpServer server;
    private ExecutorService serverThreads;
    private String previousPolicy;
    private final ConcurrentLinkedQueue<JSONObject> requests = new ConcurrentLinkedQueue<>();
    @BeforeMethod public void start() throws Exception {
        previousPolicy = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "ollama");
        requests.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/api/chat", exchange -> {
            var body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(body);
            String user = body.getJSONArray("messages").getJSONObject(1).getString("content");
            if (user.equals("slow")) {
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] response = new JSONObject().put("message", new JSONObject().put("content", "{\"candidateId\":\"c1\"}"))
                    .put("done_reason", user.equals("truncate") ? "length" : "stop")
                    .put("load_duration", 1000).toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
    }
    @AfterMethod(alwaysRun=true) public void stop() {
        if (server != null) server.stop(0);
        if (serverThreads != null) serverThreads.shutdownNow();
        if (previousPolicy == null) System.clearProperty("delivery.provider.allowlist");
        else System.setProperty("delivery.provider.allowlist", previousPolicy);
    }
    private LocalLlmClient client(String model) {
        return new LocalLlmClient("http://127.0.0.1:" + server.getAddress().getPort(), model, Duration.ofSeconds(3), 8192);
    }
    @Test public void rankingCapsOutputButGenerationKeepsItsBudget() throws Exception {
        var client = client("model-a");
        client.completeRankingJson("system", "rank");
        client.completeJson("system", "generate");
        var bodies = requests.toArray(JSONObject[]::new);
        Assert.assertEquals(bodies[0].getJSONObject("options").getInt("num_predict"), 512);
        Assert.assertFalse(bodies[0].getBoolean("think"));
        Assert.assertFalse(bodies[1].has("think"), "Generation retains model settings");
        Assert.assertEquals(bodies[1].getJSONObject("options").getInt("num_predict"), 8192);
    }
    @Test public void truncatedRankingFailsAndRestoresFollowingRequest() throws Exception {
        var client = client("model-a");
        Assert.expectThrows(java.io.IOException.class, () -> client.completeRankingJson("system", "truncate"));
        client.completeJson("system", "generate");
        Assert.assertEquals(requests.toArray(JSONObject[]::new)[1].getJSONObject("options").getInt("num_predict"), 8192);
    }
    @Test public void concurrentClientsKeepModelAndBudgetIsolated() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var ranking = pool.submit(() -> client("rank-model").completeRankingJson("sys", "rank"));
            var generation = pool.submit(() -> client("generate-model").completeJson("sys", "generate"));
            ranking.get(5, TimeUnit.SECONDS); generation.get(5, TimeUnit.SECONDS);
            Assert.assertEquals(requests.size(), 2);
            for (var body : requests) {
                int expected = body.getString("model").equals("rank-model") ? 512 : 8192;
                Assert.assertEquals(body.getJSONObject("options").getInt("num_predict"), expected);
            }
        } finally { pool.shutdownNow(); }
    }

    @Test public void timeoutRestoresBudgetForTheNextRequest() throws Exception {
        var shortClient = new LocalLlmClient("http://127.0.0.1:" + server.getAddress().getPort(), "rank-model", Duration.ofMillis(100), 8192);
        Assert.expectThrows(java.net.http.HttpTimeoutException.class, () -> shortClient.completeRankingJson("sys", "slow"));
        client("generate-model").completeJson("sys", "after-timeout");
        var subsequent = requests.stream().filter(body -> body.getJSONArray("messages").getJSONObject(1)
                .getString("content").equals("after-timeout")).findFirst().orElseThrow();
        Assert.assertEquals(subsequent.getJSONObject("options").getInt("num_predict"), 8192);
    }
}
