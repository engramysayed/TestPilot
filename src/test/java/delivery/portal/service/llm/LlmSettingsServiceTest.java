package delivery.portal.service.llm;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.llm.LlmSettingsService.UpdateRequest;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LlmSettingsServiceTest {
    private Path storeRoot;
    private DeliveryPortalProperties props;
    private String prevCursor;
    private String prevAgent;
    private String prevOllama;
    private String prevLlmModel;
    private String prevGenModel;
    private String prevBase;
    private String prevVision;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("llm-settings-test-");
        props = new DeliveryPortalProperties();
        props.setStoreRoot(storeRoot.toString());
        props.setLlmBaseUrl("http://127.0.0.1:11434");
        props.setLlmModel("gemma4:e2b");
        props.setGenerateModel("gemma4:e2b");
        prevCursor = System.getProperty("CURSOR_API_KEY");
        prevAgent = System.getProperty("AGENTROUTER_API_KEY");
        prevOllama = System.getProperty("OLLAMA_API_KEY");
        prevLlmModel = System.getProperty("delivery.llm-model");
        prevGenModel = System.getProperty("delivery.generate-model");
        prevBase = System.getProperty("delivery.llm-base-url");
        prevVision = System.getProperty("delivery.vision.assert.model");
        System.clearProperty("CURSOR_API_KEY");
        System.clearProperty("AGENTROUTER_API_KEY");
        System.clearProperty("OLLAMA_API_KEY");
        System.clearProperty("delivery.llm-model");
        System.clearProperty("delivery.generate-model");
        System.clearProperty("delivery.llm-base-url");
        System.clearProperty("delivery.vision.assert.model");
    }

    @AfterMethod
    public void tearDown() {
        restore("CURSOR_API_KEY", prevCursor);
        restore("AGENTROUTER_API_KEY", prevAgent);
        restore("OLLAMA_API_KEY", prevOllama);
        restore("delivery.llm-model", prevLlmModel);
        restore("delivery.generate-model", prevGenModel);
        restore("delivery.llm-base-url", prevBase);
        restore("delivery.vision.assert.model", prevVision);
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }

    @Test
    public void maskOnGetNeverExposesRawSecret() {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.update(new UpdateRequest(
                "super-secret-cursor-key-ABCD",
                null, null, null, null, null,
                false, false, false));
        Map<String, Object> view = svc.maskedView();
        Assert.assertEquals(view.get("cursorApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(view.get("cursorApiKeyLast4"), "ABCD");
        Assert.assertFalse(view.containsKey("cursorApiKey"));
        String dump = view.toString();
        Assert.assertFalse(dump.contains("super-secret-cursor-key"));
    }

    @Test
    public void putSecretThenGetConfiguredAndBlankLeavesUnchanged() {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.update(new UpdateRequest(
                "cursor-KEY99",
                "agent-KEY88",
                "ollama-KEY77",
                "http://127.0.0.1:11434",
                "gemma4:e2b",
                "qwen2.5vl:7b",
                false, false, false));
        Map<String, Object> after = svc.maskedView();
        Assert.assertEquals(after.get("cursorApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(after.get("cursorApiKeyLast4"), "EY99");
        Assert.assertEquals(after.get("agentrouterApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(after.get("ollamaApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(after.get("ollamaChatModel"), "gemma4:e2b");
        Assert.assertEquals(after.get("ollamaVisionModel"), "qwen2.5vl:7b");

        // blank/omit secrets leave unchanged; chat model can still update
        svc.update(new UpdateRequest(
                "",
                null,
                "   ",
                "http://127.0.0.1:11434",
                "qwen2.5:latest",
                null,
                false, false, false));
        Map<String, Object> kept = svc.maskedView();
        Assert.assertEquals(kept.get("cursorApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(kept.get("cursorApiKeyLast4"), "EY99");
        Assert.assertEquals(kept.get("agentrouterApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(kept.get("ollamaApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(kept.get("ollamaChatModel"), "qwen2.5:latest");
        Assert.assertEquals(System.getProperty("CURSOR_API_KEY"), "cursor-KEY99");
        Assert.assertEquals(System.getProperty("OLLAMA_API_KEY"), "ollama-KEY77");
        Assert.assertEquals(props.getLlmModel(), "qwen2.5:latest");
        Assert.assertEquals(props.getGenerateModel(), "qwen2.5:latest");
        Assert.assertEquals(System.getProperty("delivery.vision.assert.model"), "qwen2.5vl:7b");
    }

    @Test
    public void clearFlagsWipeSecrets() {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.update(new UpdateRequest("abc12345", "def67890", "ghi11223", null, null, null, false, false, false));
        // Confirm publish landed before clear (when ambient env for that key is unset).
        if (System.getenv("CURSOR_API_KEY") == null || System.getenv("CURSOR_API_KEY").isBlank()) {
            Assert.assertEquals(System.getProperty("CURSOR_API_KEY"), "abc12345");
        }
        svc.update(new UpdateRequest(null, null, null, null, null, null, true, true, true));
        Map<String, Object> view = svc.maskedView();
        Assert.assertEquals(view.get("cursorApiKeyConfigured"), Boolean.FALSE);
        Assert.assertEquals(view.get("agentrouterApiKeyConfigured"), Boolean.FALSE);
        Assert.assertEquals(view.get("ollamaApiKeyConfigured"), Boolean.FALSE);
        // Clear must drop System properties without restart when ambient env is unset
        // (same skip pattern as ApiKeySourceTest — never assert against ambient secret env).
        assertSysPropClearedUnlessEnv("CURSOR_API_KEY");
        assertSysPropClearedUnlessEnv("AGENTROUTER_API_KEY");
        assertSysPropClearedUnlessEnv("OLLAMA_API_KEY");
    }

    /**
     * When ambient getenv for {@code key} is set, skip the System-property assertion
     * (env wins and publishSecretProp must not touch the property).
     * Otherwise assert clearProperty left getProperty null/blank.
     */
    private static void assertSysPropClearedUnlessEnv(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            // skip: ambient env set for this key
            return;
        }
        String prop = System.getProperty(key);
        Assert.assertTrue(prop == null || prop.isBlank(),
                key + " System property must be null/blank after clearFlags (got non-blank)");
    }

    @Test
    public void envBlank_matchesGetenvReality() {
        // Documents helper used by applyToRuntime to skip props.set* when env present.
        Assert.assertEquals(
                LlmSettingsService.envBlank("delivery.llm-model"),
                firstNonBlank(System.getenv("delivery.llm-model"), System.getenv("DELIVERY_LLM_MODEL")) == null);
        Assert.assertEquals(
                LlmSettingsService.envBlank("delivery.llm-base-url"),
                firstNonBlank(System.getenv("delivery.llm-base-url"), System.getenv("DELIVERY_LLM_BASE_URL")) == null);
        Assert.assertEquals(
                LlmSettingsService.envBlank("delivery.generate-model"),
                firstNonBlank(System.getenv("delivery.generate-model"), System.getenv("DELIVERY_GENERATE_MODEL")) == null);
    }

    /**
     * Env wins for URL/model: when DELIVERY_LLM_MODEL (or delivery.llm-model) is set in the
     * process environment, applyToRuntime leaves props.llmModel / generateModel unchanged.
     * When env is blank, stored settings update props (complementary path).
     * JVM cannot inject getenv; branch on ambient reality (ApiKeySourceTest style).
     */
    @Test
    public void applyToRuntime_envWinsSkipsPropsSetForModel() {
        String env = firstNonBlank(System.getenv("delivery.llm-model"), System.getenv("DELIVERY_LLM_MODEL"));
        if (env != null && !env.isBlank()) {
            // ambient env: apply must not overwrite props with stored settings
            props.setLlmModel("props-before");
            props.setGenerateModel("props-before");
            LlmSettingsService svc = new LlmSettingsService(props);
            svc.update(new UpdateRequest(null, null, null, null, "stored-model", null, false, false, false));
            Assert.assertEquals(props.getLlmModel(), "props-before");
            // generate-model may still update if its own env is blank
            if (!LlmSettingsService.envBlank("delivery.generate-model")) {
                Assert.assertEquals(props.getGenerateModel(), "props-before");
            }
        } else {
            // no ambient env: stored model applies to props (documents the complementary path)
            LlmSettingsService svc = new LlmSettingsService(props);
            svc.update(new UpdateRequest(null, null, null, null, "from-settings", null, false, false, false));
            Assert.assertEquals(props.getLlmModel(), "from-settings");
            if (LlmSettingsService.envBlank("delivery.generate-model")) {
                Assert.assertEquals(props.getGenerateModel(), "from-settings");
            }
        }
    }

    @Test
    public void applyToRuntime_envWinsSkipsPropsSetForBaseUrl() {
        String env = firstNonBlank(System.getenv("delivery.llm-base-url"), System.getenv("DELIVERY_LLM_BASE_URL"));
        if (env != null && !env.isBlank()) {
            props.setLlmBaseUrl("http://props-before:11434");
            LlmSettingsService svc = new LlmSettingsService(props);
            svc.update(new UpdateRequest(null, null, null, "http://stored:11434", null, null, false, false, false));
            Assert.assertEquals(props.getLlmBaseUrl(), "http://props-before:11434");
        } else {
            LlmSettingsService svc = new LlmSettingsService(props);
            svc.update(new UpdateRequest(null, null, null, "http://from-settings:11434", null, null, false, false, false));
            Assert.assertEquals(props.getLlmBaseUrl(), "http://from-settings:11434");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    @Test
    public void ollamaTagsParserReadsFixtureNames() throws Exception {
        String json = Files.readString(
                Path.of("src/test/resources/fixtures/ollama-tags.json"), StandardCharsets.UTF_8);
        List<String> names = LlmSettingsService.parseOllamaTagsJson(json);
        Assert.assertEquals(names, List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
    }

    @Test
    public void persistsEncryptedFileUnderStoreRoot() throws Exception {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.update(new UpdateRequest("persist-secret-ZZ99", null, null, null, "mychat", null, false, false, false));
        Path file = storeRoot.resolve("llm-settings.json");
        Assert.assertTrue(Files.isRegularFile(file));
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        Assert.assertFalse(raw.contains("persist-secret-ZZ99"));
        Assert.assertTrue(raw.contains("cursorApiKeyEnc"));
        Assert.assertTrue(raw.contains("mychat"));

        // reload from disk via new instance
        DeliveryPortalProperties props2 = new DeliveryPortalProperties();
        props2.setStoreRoot(storeRoot.toString());
        props2.setLlmBaseUrl("http://127.0.0.1:11434");
        System.clearProperty("CURSOR_API_KEY");
        LlmSettingsService reloaded = new LlmSettingsService(props2);
        Map<String, Object> view = reloaded.maskedView();
        Assert.assertEquals(view.get("cursorApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(view.get("cursorApiKeyLast4"), "ZZ99");
        Assert.assertEquals(view.get("ollamaChatModel"), "mychat");
        Assert.assertEquals(System.getProperty("CURSOR_API_KEY"), "persist-secret-ZZ99");
    }

    @Test
    public void chatModels_dropsVisionLikeNames_keepsChat() {
        List<String> all = LlmSettingsService.parseOllamaTagsJson("""
                {"models":[
                  {"name":"gemma4:e2b"},
                  {"name":"qwen2.5vl:7b"},
                  {"name":"llava:latest"},
                  {"name":"ui-tars"},
                  {"name":"foo-vision:latest"},
                  {"name":"qwen2.5:latest"}
                ]}
                """);
        Assert.assertEquals(
                LlmSettingsService.chatModels(all),
                List.of("gemma4:e2b", "qwen2.5:latest"));
    }

    @Test
    public void isVisionModel_nullOrBlank_isFalse() {
        Assert.assertFalse(LlmSettingsService.isVisionModel(null));
        Assert.assertFalse(LlmSettingsService.isVisionModel("  "));
        Assert.assertFalse(LlmSettingsService.isVisionModel("gemma4:e2b"));
    }

    @Test
    public void rememberOllamaCache_roundTripsNames_notSecrets() throws Exception {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.update(new UpdateRequest("persist-secret-ZZ99", null, null, null, "mychat", null, false, false, false));
        svc.rememberOllamaCache(List.of("gemma4:e2b", "qwen2.5vl:7b", "  ", "qwen2.5:latest"));
        Assert.assertEquals(svc.cachedOllamaModels(), List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
        String raw = Files.readString(storeRoot.resolve("llm-settings.json"), StandardCharsets.UTF_8);
        Assert.assertTrue(raw.contains("cachedOllamaModels"));
        Assert.assertTrue(raw.contains("gemma4:e2b"));
        Assert.assertFalse(raw.contains("persist-secret-ZZ99"));

        DeliveryPortalProperties props2 = new DeliveryPortalProperties();
        props2.setStoreRoot(storeRoot.toString());
        props2.setLlmBaseUrl("http://127.0.0.1:11434");
        LlmSettingsService reloaded = new LlmSettingsService(props2);
        Assert.assertEquals(reloaded.cachedOllamaModels(), List.of("gemma4:e2b", "qwen2.5vl:7b", "qwen2.5:latest"));
    }

    @Test
    public void updateSecrets_preservesCachedModels() {
        LlmSettingsService svc = new LlmSettingsService(props);
        svc.rememberOllamaCache(List.of("gemma4:e2b"));
        svc.update(new UpdateRequest(null, null, null, null, "other-chat", null, false, false, false));
        Assert.assertEquals(svc.cachedOllamaModels(), List.of("gemma4:e2b"));
    }
}