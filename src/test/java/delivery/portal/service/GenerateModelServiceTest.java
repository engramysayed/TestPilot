package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.llm.LlmSettingsService;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GenerateModelServiceTest {
    private Path storeRoot;
    private DeliveryPortalProperties props;
    private LlmSettingsService llm;
    private GenerateModelService models;
    private String prevBase;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("gms-test-");
        props = new DeliveryPortalProperties();
        props.setStoreRoot(storeRoot.toString());
        props.setLlmBaseUrl("http://127.0.0.1:9");
        props.setLlmModel("gemma4:e2b");
        props.setGenerateModel("gemma4:e2b");
        props.setGenerateModels(List.of("gemma4:e2b", "qwen2.5:latest"));
        prevBase = System.getProperty("delivery.llm-base-url");
        System.clearProperty("delivery.llm-base-url");
        llm = new LlmSettingsService(props);
        models = new GenerateModelService(props, llm);
    }

    @AfterMethod
    public void tearDown() {
        if (prevBase == null) {
            System.clearProperty("delivery.llm-base-url");
        } else {
            System.setProperty("delivery.llm-base-url", prevBase);
        }
    }

    @Test
    public void resolve_usesDefaultWhenBlank() {
        llm.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5:latest"));
        Assert.assertEquals(models.resolve(null), "gemma4:e2b");
        Assert.assertEquals(models.resolve("  "), "gemma4:e2b");
    }

    @Test
    public void resolve_acceptsConfiguredModel() {
        llm.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5:latest"));
        Assert.assertEquals(models.resolve("qwen2.5:latest"), "qwen2.5:latest");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void resolve_rejectsUnknownModel() {
        llm.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5:latest"));
        models.resolve("llama3:latest");
    }

    @Test
    public void resolve_acceptsCachedNameNotInHardcodedPair() {
        llm.rememberOllamaCache(List.of("mistral:latest", "gemma4:e2b"));
        Assert.assertEquals(models.resolve("mistral:latest"), "mistral:latest");
    }

    @Test
    public void configPayload_includesSourceAndUnreachable() {
        Map<String, Object> body = models.configPayload(false);
        Assert.assertTrue(body.containsKey("source"));
        Assert.assertTrue(body.containsKey("unreachable"));
        Assert.assertTrue(body.get("models") instanceof List);
    }

    @Test
    public void configPayload_listsModelsAndDefault() {
        llm.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
        Map<String, Object> payload = models.configPayload(false);
        Assert.assertEquals(payload.get("defaultModel"), "gemma4:e2b");
        Assert.assertEquals(payload.get("models"), List.of("gemma4:e2b", "qwen2.5:latest"));
        Assert.assertEquals(payload.get("source"), "cache");
        Assert.assertEquals(payload.get("unreachable"), Boolean.FALSE);
    }
}