package delivery.portal.service.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.authoring.AuthoringVerbAliases;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.security.JobSecretCrypto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import utils.LogsManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Install-wide OWNER LLM / heal settings persisted under {@code delivery-store/llm-settings.json}.
 * Secrets are encrypted with {@link JobSecretCrypto}; runtime publish uses System properties
 * (env wins when already set — same semantics as {@code LocalApiKeyLoader}).
 */
@Service
public class LlmSettingsService implements ApplicationRunner {
    static final String FILE_NAME = "llm-settings.json";
    static final String DEFAULT_OLLAMA_BASE = "http://127.0.0.1:11434";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DeliveryPortalProperties props;
    private final Path settingsFile;
    private final HttpClient http;
    private final Object lock = new Object();

    private volatile StoredSettings stored = StoredSettings.empty();

    @Autowired
    public LlmSettingsService(DeliveryPortalProperties props) {
        this(props, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Test seam — package-private HttpClient injection. */
    LlmSettingsService(DeliveryPortalProperties props, HttpClient http) {
        this.props = Objects.requireNonNull(props, "props");
        this.http = Objects.requireNonNull(http, "http");
        String root = props.getStoreRoot() == null || props.getStoreRoot().isBlank()
                ? "./delivery-store"
                : props.getStoreRoot().trim();
        this.settingsFile = Path.of(root).resolve(FILE_NAME).toAbsolutePath().normalize();
        loadFromDisk();
        applyToRuntime();
    }

    @Override
    public void run(ApplicationArguments args) {
        loadFromDisk();
        applyToRuntime();
    }

    public Map<String, Object> maskedView() {
        StoredSettings s = snapshot();
        Map<String, Object> out = new LinkedHashMap<>();
        putSecretMask(out, "cursorApiKey", s.cursorApiKeyPlain);
        putSecretMask(out, "agentrouterApiKey", s.agentrouterApiKeyPlain);
        putSecretMask(out, "ollamaApiKey", s.ollamaApiKeyPlain);
        out.put("ollamaBaseUrl", blankToDefault(s.ollamaBaseUrl, defaultBaseUrl()));
        out.put("ollamaChatModel", nullToEmpty(s.ollamaChatModel));
        out.put("ollamaVisionModel", nullToEmpty(s.ollamaVisionModel));
        out.put("reviewCasesPerBatch", s.reviewCasesPerBatch);
        out.put("browser", s.browser);
        out.put("authoringVerbAliases", AuthoringVerbAliases.toMaps(s.authoringVerbAliases));
        out.put("builtInVerbs", AuthoringVerbAliases.builtInMaps());
        out.put("codeOnlyVerbs", AuthoringVerbAliases.codeOnlySpecials());
        return out;
    }

    public Map<String, Object> update(UpdateRequest req) {
        synchronized (lock) {
            StoredSettings cur = stored;
            String cursor = cur.cursorApiKeyPlain;
            String agent = cur.agentrouterApiKeyPlain;
            String ollamaKey = cur.ollamaApiKeyPlain;
            if (req != null) {
                if (req.clearCursorApiKey) {
                    cursor = "";
                } else if (req.cursorApiKey != null && !req.cursorApiKey.isBlank()) {
                    cursor = req.cursorApiKey.trim();
                }
                if (req.clearAgentrouterApiKey) {
                    agent = "";
                } else if (req.agentrouterApiKey != null && !req.agentrouterApiKey.isBlank()) {
                    agent = req.agentrouterApiKey.trim();
                }
                if (req.clearOllamaApiKey) {
                    ollamaKey = "";
                } else if (req.ollamaApiKey != null && !req.ollamaApiKey.isBlank()) {
                    ollamaKey = req.ollamaApiKey.trim();
                }
            }
            String baseUrl = cur.ollamaBaseUrl;
            String chat = cur.ollamaChatModel;
            String vision = cur.ollamaVisionModel;
            int batch = cur.reviewCasesPerBatch;
            String browser = cur.browser;
            List<AuthoringVerbAliases.Alias> aliases = cur.authoringVerbAliases;
            if (req != null) {
                if (req.ollamaBaseUrl != null) {
                    baseUrl = req.ollamaBaseUrl.trim();
                }
                if (req.ollamaChatModel != null) {
                    chat = req.ollamaChatModel.trim();
                }
                if (req.ollamaVisionModel != null) {
                    vision = req.ollamaVisionModel.trim();
                }
                if (req.reviewCasesPerBatch != null) {
                    batch = delivery.portal.service.ReviewBatchPolicy.clampN(req.reviewCasesPerBatch);
                }
                if (req.browser != null && !req.browser.isBlank()) {
                    browser = normalizeBrowser(req.browser);
                }
                if (req.authoringVerbAliases != null) {
                    aliases = AuthoringVerbAliases.normalize(req.authoringVerbAliases);
                }
            }
            List<String> cache = cur.cachedOllamaModels == null ? List.of() : cur.cachedOllamaModels;
            StoredSettings next = new StoredSettings(
                    cursor, agent, ollamaKey, baseUrl, chat, vision, cache, batch, browser, aliases);
            writeToDisk(next);
            stored = next;
            applyToRuntime();
            return maskedView();
        }
    }

    /**
     * Calls {@code {baseUrl}/api/tags}. Uses saved base URL unless {@code baseUrlOverride} is non-blank.
     * Sends Bearer auth when an Ollama API key is configured.
     */
    public List<String> listOllamaModels(String baseUrlOverride) throws OllamaUnreachableException {
        String base = (baseUrlOverride != null && !baseUrlOverride.isBlank())
                ? baseUrlOverride.trim()
                : blankToDefault(snapshot().ollamaBaseUrl, defaultBaseUrl());
        base = trimTrailingSlash(base);
        String url = base + "/api/tags";
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET();
            String key = snapshot().ollamaApiKeyPlain;
            if (key != null && !key.isBlank()) {
                b.header("Authorization", "Bearer " + key);
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new OllamaUnreachableException("Ollama returned HTTP " + res.statusCode() + " for " + url);
            }
            List<String> names = parseOllamaTagsJson(res.body());
            rememberOllamaCache(names);
            return names;
        } catch (OllamaUnreachableException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaUnreachableException("Ollama unreachable at " + url + ": " + e.getMessage(), e);
        }
    }

    /** Package-visible for unit tests — parses Ollama {@code /api/tags} JSON into model name list. */
    public static List<String> parseOllamaTagsJson(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> root = MAPPER.readValue(body, MAP_TYPE);
            Object models = root.get("models");
            if (!(models instanceof List<?> list)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof Map<?, ?> m) {
                    Object name = m.get("name");
                    if (name != null) {
                        String n = String.valueOf(name).trim();
                        if (!n.isEmpty()) {
                            names.add(n);
                        }
                    }
                }
            }
            return names;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ollama tags JSON: " + e.getMessage(), e);
        }
    }



    public record GenerateModelList(List<String> models, String defaultModel, String source, boolean unreachable) {
    }

    private static final List<String> FALLBACK_GENERATE_MODELS = List.of("gemma4:e2b", "qwen2.5:latest");

    public GenerateModelList listForGenerate(boolean refresh) {
        List<String> cachedChat = chatModels(cachedOllamaModels());
        if (!refresh && !cachedChat.isEmpty()) {
            return new GenerateModelList(cachedChat, pickDefault(cachedChat), "cache", false);
        }
        try {
            List<String> all = listOllamaModels(null);
            List<String> chat = chatModels(all);
            if (chat.isEmpty()) {
                return failForGenerate(cachedChat);
            }
            return new GenerateModelList(chat, pickDefault(chat), "live", false);
        } catch (Exception e) {
            return failForGenerate(cachedChat);
        }
    }

    private GenerateModelList failForGenerate(List<String> cachedChat) {
        if (cachedChat != null && !cachedChat.isEmpty()) {
            return new GenerateModelList(cachedChat, pickDefault(cachedChat), "cache", true);
        }
        return new GenerateModelList(FALLBACK_GENERATE_MODELS, pickDefault(FALLBACK_GENERATE_MODELS), "fallback", true);
    }

    private String pickDefault(List<String> chat) {
        if (chat == null || chat.isEmpty()) {
            return "";
        }
        String gen = props.getGenerateModel();
        if (gen != null && !gen.isBlank() && chat.contains(gen.trim())) {
            return gen.trim();
        }
        String adminChat = snapshot().ollamaChatModel;
        if (adminChat != null && !adminChat.isBlank() && chat.contains(adminChat.trim())) {
            return adminChat.trim();
        }
        return chat.get(0);
    }

    public static boolean isVisionModel(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("vl") || n.contains("llava") || n.contains("ui-tars") || n.contains("vision");
    }

    public static List<String> chatModels(List<String> all) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String name : all) {
            if (name == null || name.isBlank() || isVisionModel(name)) {
                continue;
            }
            out.add(name.trim());
        }
        return List.copyOf(out);
    }


    public void rememberOllamaCache(List<String> names) {
        synchronized (lock) {
            List<String> cleaned = new ArrayList<>();
            if (names != null) {
                for (String name : names) {
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    cleaned.add(name.trim());
                }
            }
            List<String> cache = List.copyOf(cleaned);
            StoredSettings cur = stored;
            StoredSettings next = new StoredSettings(
                    cur.cursorApiKeyPlain,
                    cur.agentrouterApiKeyPlain,
                    cur.ollamaApiKeyPlain,
                    cur.ollamaBaseUrl,
                    cur.ollamaChatModel,
                    cur.ollamaVisionModel,
                    cache,
                    cur.reviewCasesPerBatch,
                    cur.browser,
                    cur.authoringVerbAliases);
            writeToDisk(next);
            stored = next;
        }
    }

    public List<String> cachedOllamaModels() {
        List<String> c = snapshot().cachedOllamaModels;
        return c == null ? List.of() : c;
    }

    public void applyToRuntime() {
        StoredSettings s = snapshot();
        publishSecretProp("CURSOR_API_KEY", s.cursorApiKeyPlain);
        publishSecretProp("AGENTROUTER_API_KEY", s.agentrouterApiKeyPlain);
        publishSecretProp("OLLAMA_API_KEY", s.ollamaApiKeyPlain);

        String base = blankToDefault(s.ollamaBaseUrl, defaultBaseUrl());
        if (base != null && !base.isBlank()) {
            // Env wins for in-memory props too (same names as setSysPropIfEnvBlank).
            if (envBlank("delivery.llm-base-url")) {
                props.setLlmBaseUrl(base);
            }
            setSysPropIfEnvBlank("delivery.llm-base-url", base);
        }
        if (s.ollamaChatModel != null && !s.ollamaChatModel.isBlank()) {
            if (envBlank("delivery.llm-model")) {
                props.setLlmModel(s.ollamaChatModel);
            }
            if (envBlank("delivery.generate-model")) {
                props.setGenerateModel(s.ollamaChatModel);
            }
            setSysPropIfEnvBlank("delivery.llm-model", s.ollamaChatModel);
            setSysPropIfEnvBlank("delivery.generate-model", s.ollamaChatModel);
        }
        if (s.ollamaVisionModel != null && !s.ollamaVisionModel.isBlank()) {
            // Vision keys: System props only; setSysPropIfEnvBlank already no-ops when env set.
            setSysPropIfEnvBlank("delivery.vision.assert.model", s.ollamaVisionModel);
            // Also publish grounding model when the vision path is ollama/qwen-oriented.
            String lower = s.ollamaVisionModel.toLowerCase();
            if (lower.startsWith("qwen") || lower.contains("vl") || lower.contains("vision")) {
                setSysPropIfEnvBlank("delivery.vision.grounding.model", s.ollamaVisionModel);
                setSysPropIfEnvBlank("delivery.vision.model", s.ollamaVisionModel);
            }
        }
        publishBrowser(s.browser);
        AuthoringVerbAliases.publish(s.authoringVerbAliases);
    }

    public int reviewCasesPerBatch() {
        return delivery.portal.service.ReviewBatchPolicy.clampN(snapshot().reviewCasesPerBatch);
    }

    public String browserChoice() {
        return normalizeBrowser(snapshot().browser);
    }

    private void loadFromDisk() {
        synchronized (lock) {
            if (!Files.isRegularFile(settingsFile)) {
                stored = defaultsFromProps();
                return;
            }
            try {
                Map<String, Object> raw = MAPPER.readValue(Files.readString(settingsFile, StandardCharsets.UTF_8), MAP_TYPE);
                stored = fromFileMap(raw);
            } catch (Exception e) {
                LogsManager.warn("Could not load llm-settings.json: " + e.getMessage());
                stored = defaultsFromProps();
            }
        }
    }

    private void writeToDisk(StoredSettings s) {
        try {
            Files.createDirectories(settingsFile.getParent());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("cursorApiKeyEnc", encryptIfPresent(s.cursorApiKeyPlain));
            raw.put("agentrouterApiKeyEnc", encryptIfPresent(s.agentrouterApiKeyPlain));
            raw.put("ollamaApiKeyEnc", encryptIfPresent(s.ollamaApiKeyPlain));
            raw.put("ollamaBaseUrl", nullToEmpty(s.ollamaBaseUrl));
            raw.put("ollamaChatModel", nullToEmpty(s.ollamaChatModel));
            raw.put("ollamaVisionModel", nullToEmpty(s.ollamaVisionModel));
            raw.put("cachedOllamaModels", s.cachedOllamaModels() == null ? List.of() : s.cachedOllamaModels());
            raw.put("reviewCasesPerBatch", s.reviewCasesPerBatch);
            raw.put("browser", nullToEmpty(s.browser));
            raw.put("authoringVerbAliases", AuthoringVerbAliases.toMaps(s.authoringVerbAliases));
            Path tmp = settingsFile.resolveSibling(FILE_NAME + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), raw);
            try {
                Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write llm-settings.json: " + e.getMessage(), e);
        }
    }

    private StoredSettings fromFileMap(Map<String, Object> raw) {
        if (raw == null) {
            return defaultsFromProps();
        }
        String cursor = decryptEnc(str(raw.get("cursorApiKeyEnc")));
        String agent = decryptEnc(str(raw.get("agentrouterApiKeyEnc")));
        String ollamaKey = decryptEnc(str(raw.get("ollamaApiKeyEnc")));
        String base = str(raw.get("ollamaBaseUrl"));
        String chat = str(raw.get("ollamaChatModel"));
        String vision = str(raw.get("ollamaVisionModel"));
        if (base.isBlank()) {
            base = defaultBaseUrl();
        }
        List<String> cache = parseCachedModels(raw.get("cachedOllamaModels"));
        int batch = parseBatch(raw.get("reviewCasesPerBatch"));
        String browser = normalizeBrowser(str(raw.get("browser")));
        List<AuthoringVerbAliases.Alias> aliases = parseAliases(raw);
        return new StoredSettings(cursor, agent, ollamaKey, base, chat, vision, cache, batch, browser, aliases);
    }

    private static List<AuthoringVerbAliases.Alias> parseAliases(Map<String, Object> raw) {
        if (raw == null || !raw.containsKey("authoringVerbAliases")) {
            return AuthoringVerbAliases.seed();
        }
        Object value = raw.get("authoringVerbAliases");
        if (value == null) {
            return AuthoringVerbAliases.seed();
        }
        try {
            return AuthoringVerbAliases.fromRaw(value);
        } catch (IllegalArgumentException e) {
            LogsManager.warn("Ignoring invalid authoringVerbAliases: " + e.getMessage());
            return AuthoringVerbAliases.seed();
        }
    }

    private static List<String> parseCachedModels(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String n = String.valueOf(item).trim();
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return List.copyOf(out);
    }

    private StoredSettings defaultsFromProps() {
        String base = props.getLlmBaseUrl();
        if (base == null || base.isBlank()) {
            base = DEFAULT_OLLAMA_BASE;
        }
        String chat = props.getLlmModel();
        if (chat == null || chat.isBlank()) {
            chat = props.getGenerateModel();
        }
        return new StoredSettings("", "", "", base, nullToEmpty(chat), "", List.of(),
                delivery.portal.service.ReviewBatchPolicy.DEFAULT_N, "edge", AuthoringVerbAliases.seed());
    }

    private String defaultBaseUrl() {
        String b = props.getLlmBaseUrl();
        return (b == null || b.isBlank()) ? DEFAULT_OLLAMA_BASE : b.trim();
    }

    private StoredSettings snapshot() {
        return stored;
    }

    private static void putSecretMask(Map<String, Object> out, String field, String plain) {
        boolean configured = plain != null && !plain.isBlank();
        out.put(field + "Configured", configured);
        out.put(field + "Last4", configured ? last4(plain) : null);
    }

    static String last4(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        String t = secret.trim();
        if (t.length() <= 4) {
            return t;
        }
        return t.substring(t.length() - 4);
    }

    private static String encryptIfPresent(String plain) {
        if (plain == null || plain.isBlank()) {
            return "";
        }
        return JobSecretCrypto.encrypt(plain);
    }

    private static String decryptEnc(String enc) {
        if (enc == null || enc.isBlank()) {
            return "";
        }
        return JobSecretCrypto.decrypt(enc);
    }

    /**
     * Env wins: do not touch the System property when {@code getenv(key)} is non-blank.
     * Non-blank stored + blank env -> {@code System.setProperty}.
     * Blank stored + blank env -> {@code System.clearProperty} so a prior publish drops
     * without restart (CURSOR_API_KEY / AGENTROUTER_API_KEY / OLLAMA_API_KEY).
     * Never logs secret values.
     */
    private static void publishSecretProp(String key, String value) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return;
        }
        if (value == null || value.isBlank()) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    /**
     * True when both {@code getenv(key)} and the env-style uppercase form
     * ({@code key} with {@code '.' -> '_'} uppercased) are blank or absent.
     * Package-visible for unit tests.
     */
    static boolean envBlank(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return false;
        }
        String envAlt = System.getenv(key.replace('.', '_').toUpperCase());
        return envAlt == null || envAlt.isBlank();
    }

    private static void setSysPropIfEnvBlank(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!envBlank(key)) {
            return;
        }
        System.setProperty(key, value);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public record UpdateRequest(
            String cursorApiKey,
            String agentrouterApiKey,
            String ollamaApiKey,
            String ollamaBaseUrl,
            String ollamaChatModel,
            String ollamaVisionModel,
            boolean clearCursorApiKey,
            boolean clearAgentrouterApiKey,
            boolean clearOllamaApiKey,
            Integer reviewCasesPerBatch,
            String browser,
            List<AuthoringVerbAliases.Alias> authoringVerbAliases
    ) {
        public UpdateRequest(
                String cursorApiKey,
                String agentrouterApiKey,
                String ollamaApiKey,
                String ollamaBaseUrl,
                String ollamaChatModel,
                String ollamaVisionModel,
                boolean clearCursorApiKey,
                boolean clearAgentrouterApiKey,
                boolean clearOllamaApiKey) {
            this(cursorApiKey, agentrouterApiKey, ollamaApiKey, ollamaBaseUrl, ollamaChatModel,
                    ollamaVisionModel, clearCursorApiKey, clearAgentrouterApiKey, clearOllamaApiKey,
                    null, null, null);
        }

        public UpdateRequest(
                String cursorApiKey,
                String agentrouterApiKey,
                String ollamaApiKey,
                String ollamaBaseUrl,
                String ollamaChatModel,
                String ollamaVisionModel,
                boolean clearCursorApiKey,
                boolean clearAgentrouterApiKey,
                boolean clearOllamaApiKey,
                Integer reviewCasesPerBatch,
                String browser) {
            this(cursorApiKey, agentrouterApiKey, ollamaApiKey, ollamaBaseUrl, ollamaChatModel,
                    ollamaVisionModel, clearCursorApiKey, clearAgentrouterApiKey, clearOllamaApiKey,
                    reviewCasesPerBatch, browser, null);
        }
    }

    public static final class OllamaUnreachableException extends Exception {
        public OllamaUnreachableException(String message) {
            super(message);
        }

        public OllamaUnreachableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record StoredSettings(
            String cursorApiKeyPlain,
            String agentrouterApiKeyPlain,
            String ollamaApiKeyPlain,
            String ollamaBaseUrl,
            String ollamaChatModel,
            String ollamaVisionModel,
            List<String> cachedOllamaModels,
            int reviewCasesPerBatch,
            String browser,
            List<AuthoringVerbAliases.Alias> authoringVerbAliases
    ) {
        static StoredSettings empty() {
            return new StoredSettings("", "", "", DEFAULT_OLLAMA_BASE, "", "", List.of(),
                    delivery.portal.service.ReviewBatchPolicy.DEFAULT_N, "edge", AuthoringVerbAliases.seed());
        }
    }

    static String normalizeBrowser(String raw) {
        if (raw == null || raw.isBlank()) {
            return "edge";
        }
        String t = raw.trim().toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
        if (t.equals("chrome")) {
            return "chrome";
        }
        if (t.equals("chromeheadless")) {
            return "chromeheadless";
        }
        return "edge";
    }

    private static int parseBatch(Object raw) {
        if (raw instanceof Number n) {
            return delivery.portal.service.ReviewBatchPolicy.clampN(n.intValue());
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return delivery.portal.service.ReviewBatchPolicy.clampN(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return delivery.portal.service.ReviewBatchPolicy.DEFAULT_N;
            }
        }
        return delivery.portal.service.ReviewBatchPolicy.DEFAULT_N;
    }

    private static void publishBrowser(String browser) {
        String choice = normalizeBrowser(browser);
        String type = "EDGE";
        String headless = "false";
        if (choice.equals("chrome")) {
            type = "CHROME";
            headless = "false";
        } else if (choice.equals("chromeheadless")) {
            type = "CHROME";
            headless = "true";
        }
        setSysPropIfEnvBlank("BROWSER_TYPE", type);
        setSysPropIfEnvBlank("BROWSER_HEADLESS", headless);
    }
}
