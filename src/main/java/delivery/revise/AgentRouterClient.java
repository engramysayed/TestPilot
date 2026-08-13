package delivery.revise;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Anthropic-compatible Messages API client aimed at AgentRouter (or similar gateway).
 * Not used for live heal — final revise only.
 */
public class AgentRouterClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Duration timeout;

    public AgentRouterClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, Duration.ofMinutes(4));
    }

    public AgentRouterClient(String baseUrl, String apiKey, String model, Duration timeout) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.timeout = timeout == null ? Duration.ofMinutes(4) : timeout;
        if (this.apiKey.isBlank()) {
            throw new IllegalArgumentException("AgentRouter API key is blank");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Resolve from env / system properties when final revise is enabled.
     * Returns null when disabled or not configured.
     */
    public static AgentRouterClient fromConfigOrNull() {
        if (!enabled()) {
            return null;
        }
        String base = firstNonBlank(
                System.getProperty("delivery.final-revise.base-url"),
                System.getenv("AGENTROUTER_BASE_URL"),
                System.getenv("ANTHROPIC_BASE_URL"),
                utils.PropertyReader.getProperty("delivery.final-revise.base-url"),
                "https://agentrouter.org");
        String key = firstNonBlank(
                System.getProperty("delivery.final-revise.api-key"),
                System.getenv("AGENTROUTER_API_KEY"),
                System.getenv("ANTHROPIC_AUTH_TOKEN"),
                System.getenv("ANTHROPIC_API_KEY"),
                utils.PropertyReader.getProperty("delivery.final-revise.api-key"));
        String model = firstNonBlank(
                System.getProperty("delivery.final-revise.model"),
                System.getenv("AGENTROUTER_MODEL"),
                utils.PropertyReader.getProperty("delivery.final-revise.model"),
                "claude-opus-4-8");
        if (key == null || key.isBlank()) {
            return null;
        }
        return new AgentRouterClient(base, key, model);
    }

    /** Separate one-shot heal configuration; does not depend on final-revise being enabled. */
    public static AgentRouterClient fromInventConfigOrNull() {
        String base = firstNonBlank(
                System.getProperty("delivery.heal.invent.agentrouter.base-url"),
                utils.PropertyReader.getProperty("delivery.heal.invent.agentrouter.base-url"),
                System.getProperty("delivery.final-revise.base-url"),
                System.getenv("AGENTROUTER_BASE_URL"),
                utils.PropertyReader.getProperty("delivery.final-revise.base-url"),
                "https://agentrouter.org");
        String key = firstNonBlank(
                System.getProperty("delivery.heal.invent.agentrouter.api-key"),
                utils.PropertyReader.getProperty("delivery.heal.invent.agentrouter.api-key"),
                System.getenv("AGENTROUTER_API_KEY"),
                System.getenv("ANTHROPIC_AUTH_TOKEN"),
                System.getenv("ANTHROPIC_API_KEY"),
                System.getProperty("delivery.final-revise.api-key"),
                utils.PropertyReader.getProperty("delivery.final-revise.api-key"));
        String model = firstNonBlank(
                System.getProperty("delivery.heal.invent.agentrouter.model"),
                utils.PropertyReader.getProperty("delivery.heal.invent.agentrouter.model"),
                System.getenv("AGENTROUTER_MODEL"),
                System.getProperty("delivery.final-revise.model"),
                utils.PropertyReader.getProperty("delivery.final-revise.model"),
                "claude-opus-4-8");
        return key == null || key.isBlank() ? null : new AgentRouterClient(base, key, model);
    }

    public static boolean enabled() {
        String p = firstNonBlank(
                System.getProperty("delivery.final-revise.enabled"),
                System.getenv("DELIVERY_FINAL_REVISE_ENABLED"),
                utils.PropertyReader.getProperty("delivery.final-revise.enabled"),
                "true");
        return "true".equalsIgnoreCase(p) || "1".equals(p);
    }

    public String model() {
        return model;
    }

    public String completeJson(String system, String user) throws IOException, InterruptedException {
        return completeJson(system, user, null);
    }

    public String completeJson(String system, String user, byte[] imagePng)
            throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 8192);
        body.put("temperature", 0);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        JSONArray messages = new JSONArray();
        Object content = user == null ? "" : user;
        if (imagePng != null && imagePng.length > 0) {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("type", "text").put("text", content));
            parts.put(new JSONObject()
                    .put("type", "image")
                    .put("source", new JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/png")
                            .put("data", Base64.getEncoder().encodeToString(imagePng))));
            content = parts;
        }
        messages.put(new JSONObject().put("role", "user").put("content", content));
        body.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AgentRouter HTTP " + response.statusCode() + ": " + truncate(response.body(), 800));
        }
        return extractText(response.body());
    }

    static String extractText(String responseBody) throws IOException {
        JSONObject root = new JSONObject(responseBody);
        if (root.has("content") && root.get("content") instanceof JSONArray arr) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part == null) {
                    continue;
                }
                if ("text".equals(part.optString("type")) || part.has("text")) {
                    sb.append(part.optString("text", ""));
                }
            }
            String text = sb.toString().trim();
            if (!text.isBlank()) {
                return stripFences(text);
            }
        }
        // OpenAI-compat fallback
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg != null) {
                String content = msg.optString("content", "").trim();
                if (!content.isBlank()) {
                    return stripFences(content);
                }
            }
        }
        throw new IOException("AgentRouter response missing text content");
    }

    static String stripFences(String content) {
        String out = content == null ? "" : content.trim();
        out = out.replaceAll("(?is)<think>.*?</think>", "").trim();
        if (out.startsWith("```")) {
            int firstNl = out.indexOf('\n');
            int lastFence = out.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                out = out.substring(firstNl + 1, lastFence).trim();
                if (out.regionMatches(true, 0, "json", 0, 4)) {
                    out = out.substring(4).trim();
                }
            }
        }
        return out;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
