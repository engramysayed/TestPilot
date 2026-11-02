package delivery.knowledge;

import com.sun.net.httpserver.HttpServer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpKnowledgePackReaderTest {

    @Test
    public void local_getByIds_loadsAndSkipsMissing() {
        FixtureKnowledgePackReader fixture = new FixtureKnowledgePackReader()
                .put(new KnowledgePack(
                        "kp_a", "demo", "A", KnowledgePackSource.LIBRARY, 20, 1,
                        "body-a", "knowledge-packs/kp_a/pack.md", "", ""))
                .put(new KnowledgePack(
                        "kp_b", "demo", "B", KnowledgePackSource.UPLOAD_MD, 40, 2,
                        "body-b", "knowledge-packs/kp_b/pack.md", "", ""));
        HttpKnowledgePackReader reader = HttpKnowledgePackReader.local(fixture::list, fixture::get);
        Assert.assertTrue(reader.isLocalTransport());
        List<KnowledgePack> packs = reader.getByIds("demo", List.of("kp_b", "missing", "kp_a"));
        Assert.assertEquals(packs.size(), 2);
        Assert.assertEquals(packs.get(0).packId(), "kp_b");
        Assert.assertEquals(packs.get(1).packId(), "kp_a");
        Assert.assertTrue(packs.get(0).markdown().contains("body-b"));
    }

    @Test
    public void local_get_exception_degradesToEmpty() {
        HttpKnowledgePackReader reader = HttpKnowledgePackReader.local(
                projectId -> { throw new RuntimeException("boom-list"); },
                (projectId, packId) -> { throw new RuntimeException("boom-get"); }
        );
        Assert.assertTrue(reader.list("demo").isEmpty());
        Assert.assertTrue(reader.get("demo", "kp_x").isEmpty());
        Assert.assertTrue(reader.getByIds("demo", List.of("kp_x")).isEmpty());
    }

    @Test
    public void http_get_parsesMarkdown_and404Skipped() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/projects/demo/knowledge-packs/kp_ok", exchange -> {
            hits.incrementAndGet();
            byte[] body = ("{\"packId\":\"kp_ok\",\"projectId\":\"demo\",\"title\":\"Ok\","
                    + "\"source\":\"LIBRARY\",\"batchSize\":30,\"revision\":2,"
                    + "\"markdown\":\"# Hello\\n\",\"markdownPath\":\"knowledge-packs/kp_ok/pack.md\","
                    + "\"createdAt\":\"2026-11-02T00:00:00Z\",\"updatedAt\":\"2026-11-02T00:00:00Z\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/api/projects/demo/knowledge-packs/kp_missing", exchange -> {
            hits.incrementAndGet();
            byte[] body = "{\"error\":\"NOT_FOUND\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpKnowledgePackReader reader = HttpKnowledgePackReader.http(base);
            Assert.assertFalse(reader.isLocalTransport());
            Optional<KnowledgePack> ok = reader.get("demo", "kp_ok");
            Assert.assertTrue(ok.isPresent());
            Assert.assertEquals(ok.get().title(), "Ok");
            Assert.assertTrue(ok.get().markdown().contains("# Hello"));
            Assert.assertEquals(ok.get().revision(), 2);
            Assert.assertTrue(reader.get("demo", "kp_missing").isEmpty());
            List<KnowledgePack> byIds = reader.getByIds("demo", List.of("kp_ok", "kp_missing"));
            Assert.assertEquals(byIds.size(), 1);
            Assert.assertEquals(byIds.get(0).packId(), "kp_ok");
            Assert.assertTrue(hits.get() >= 3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void fromJson_parsesContractFields() {
        org.json.JSONObject o = new org.json.JSONObject()
                .put("packId", "kp_01")
                .put("projectId", "p1")
                .put("title", "T")
                .put("source", "UPLOAD_TC")
                .put("batchSize", 40)
                .put("revision", 3)
                .put("markdown", "md")
                .put("markdownPath", "knowledge-packs/kp_01/pack.md")
                .put("createdAt", "2026-11-02T01:00:00Z")
                .put("updatedAt", "2026-11-02T02:00:00Z");
        KnowledgePack p = HttpKnowledgePackReader.fromJson(o, true);
        Assert.assertEquals(p.packId(), "kp_01");
        Assert.assertEquals(p.source(), KnowledgePackSource.UPLOAD_TC);
        Assert.assertEquals(p.batchSize(), 40);
        Assert.assertEquals(p.revision(), 3);
        Assert.assertEquals(p.markdown(), "md");
    }
}
