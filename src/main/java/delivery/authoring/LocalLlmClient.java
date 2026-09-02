package delivery.authoring;

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
 * Local Ollama HTTP client. Must not target cloud LLM vendors.
 */
public class LocalLlmClient {
    private final String baseUrl;
    private final String model;
    private final Duration requestTimeout;
    private final HttpClient httpClient;

    public LocalLlmClient(String baseUrl, String model) {
        this(baseUrl, model, Duration.ofMinutes(5));
    }

    public LocalLlmClient(String baseUrl, String model, Duration requestTimeout) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.model = Objects.requireNonNull(model, "model");
        if (looksLikeCloudVendor(this.baseUrl)) {
            throw new IllegalArgumentException("Cloud LLM endpoints are not allowed on the delivery path: " + baseUrl);
        }
        this.requestTimeout = (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative())
                ? Duration.ofMinutes(5)
                : requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    Duration requestTimeout() {
        return requestTimeout;
    }

    public String completeJson(String system, String user) throws IOException, InterruptedException {
        return completeJson(system, user, (byte[]) null);
    }

    /**
     * Chat completion forcing Ollama {@code format: json}. Prefer {@link #completeChat} for
     * action/point models (UI-TARS) that emit native text.
     */
    public String completeJson(String system, String user, byte[] pngOrNull)
            throws IOException, InterruptedException {
        return completeJson(system, user, pngOrNull == null ? new byte[0][] : new byte[][]{pngOrNull});
    }

    /**
     * Chat completion forcing Ollama {@code format: json} with one or more PNG images
     * attached to the user message {@code images} array (vision models).
     */
    public String completeJson(String system, String user, byte[]... pngs)
            throws IOException, InterruptedException {
        return completeChat(system, user, true, pngs);
    }

    /**
     * Chat completion. When {@code pngOrNull} is non-empty, attaches base64 image bytes
     * for vision models ({@code message.images}).
     *
     * @param forceJson when true, sets Ollama {@code format: json} (good for schema-bound VLMs;
     *                  often harmful for UI-TARS native action strings).
     */
    public String completeChat(String system, String user, byte[] pngOrNull, boolean forceJson)
            throws IOException, InterruptedException {
        return completeChat(system, user, forceJson, pngOrNull == null ? new byte[0][] : new byte[][]{pngOrNull});
    }

    /**
     * Chat completion with zero or more PNG images on the user message.
     */
    public String completeChat(String system, String user, boolean forceJson, byte[]... pngs)
            throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system == null ? "" : system));
        JSONObject userMsg = new JSONObject().put("role", "user").put("content", user == null ? "" : user);
        JSONArray images = encodeImages(pngs);
        if (images.length() > 0) {
            userMsg.put("images", images);
        }
        messages.put(userMsg);
        body.put("messages", messages);
        if (forceJson) {
            body.put("format", "json");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
        }
        JSONObject root = new JSONObject(response.body());
        JSONObject message = root.optJSONObject("message");
        if (message == null) {
            throw new IOException("Ollama response missing message");
        }
        String content = message.optString("content", "");
        if (content.isBlank()) {
            content = message.optString("thinking", "");
        }
        return stripThinkingWrappers(content);
    }

    private static JSONArray encodeImages(byte[]... pngs) {
        JSONArray images = new JSONArray();
        if (pngs == null) {
            return images;
        }
        for (byte[] png : pngs) {
            if (png != null && png.length > 0) {
                images.put(Base64.getEncoder().encodeToString(png));
            }
        }
        return images;
    }

    static String stripThinkingWrappers(String content) {
        if (content == null) {
            return "";
        }
        String out = content.trim();
        // Some thinking models wrap scratchpad / final answer.
        out = out.replaceAll("(?is)<think>.*?</think>", "").trim();
        out = out.replaceAll("(?is)<thinking>.*?</thinking>", "").trim();
        if (out.startsWith("```")) {
            int firstNl = out.indexOf('\n');
            int lastFence = out.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                out = out.substring(firstNl + 1, lastFence).trim();
            }
        }
        return out;
    }

    public AuthorBatchResponse complete(AuthorBatchRequest request) throws IOException, InterruptedException {
        return new AuthorBatchResponse(completeJson(request.systemPrompt(), request.userPrompt()));
    }

    private static boolean looksLikeCloudVendor(String url) {
        String lower = url.toLowerCase();
        return lower.contains("googleapis.com")
                || lower.contains("openai.com")
                || lower.contains("anthropic.com")
                || lower.contains("openrouter.ai");
    }

    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
