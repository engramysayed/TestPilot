package delivery.portal.service.llm;

import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GenerateModelListTest {
    private Path storeRoot;
    private DeliveryPortalProperties props;
    private String prevBase;
    private String prevGenModel;
    private String prevLlmModel;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("gen-model-list-");
        props = new DeliveryPortalProperties();
        props.setStoreRoot(storeRoot.toString());
        props.setLlmBaseUrl("http://127.0.0.1:11434");
        props.setLlmModel("gemma4:e2b");
        props.setGenerateModel("gemma4:e2b");
        prevBase = System.getProperty("delivery.llm-base-url");
        prevGenModel = System.getProperty("delivery.generate-model");
        prevLlmModel = System.getProperty("delivery.llm-model");
        System.clearProperty("delivery.llm-base-url");
        System.clearProperty("delivery.generate-model");
        System.clearProperty("delivery.llm-model");
    }

    @AfterMethod
    public void tearDown() {
        restore("delivery.llm-base-url", prevBase);
        restore("delivery.generate-model", prevGenModel);
        restore("delivery.llm-model", prevLlmModel);
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }

    @Test
    public void listForGenerate_cacheFirst_skipsHttp() {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5vl:7b", "mistral:latest"));
        var list = svc.listForGenerate(false);
        Assert.assertEquals(list.source(), "cache");
        Assert.assertEquals(list.models(), List.of("gemma4:e2b", "mistral:latest"));
        Assert.assertFalse(list.unreachable());
    }

    @Test
    public void listForGenerate_refreshFail_usesCache() {
        props.setLlmBaseUrl("http://127.0.0.1:9");
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.rememberOllamaCache(List.of("cached-chat:latest", "qwen2.5vl:7b"));
        var list = svc.listForGenerate(true);
        Assert.assertEquals(list.source(), "cache");
        Assert.assertTrue(list.unreachable());
        Assert.assertEquals(list.models(), List.of("cached-chat:latest"));
    }

    @Test
    public void listForGenerate_refreshFail_emptyCache_fallbackTwo() {
        props.setLlmBaseUrl("http://127.0.0.1:9");
        LlmSettingsService svc = new LlmSettingsService(props);
        var list = svc.listForGenerate(true);
        Assert.assertEquals(list.source(), "fallback");
        Assert.assertTrue(list.unreachable());
        Assert.assertEquals(list.models(), List.of("gemma4:e2b", "qwen2.5:latest"));
    }

    @Test
    public void listForGenerate_liveSuccess_filtersVision_andCachesAll() throws Exception {
        String fixture = Files.readString(
                Path.of("src/test/resources/fixtures/ollama-tags.json"), StandardCharsets.UTF_8);
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = fixture.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            props.setLlmBaseUrl("http://127.0.0.1:" + port);
            LlmSettingsService svc = new LlmSettingsService(props);
            var list = svc.listForGenerate(true);
            Assert.assertEquals(list.source(), "live");
            Assert.assertFalse(list.unreachable());
            Assert.assertEquals(list.models(), List.of("gemma4:e2b", "qwen2.5:latest"));
            Assert.assertEquals(
                    svc.cachedOllamaModels(),
                    List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
        } finally {
            server.stop(0);
        }
    }
}
