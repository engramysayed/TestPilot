package delivery.portal.cli;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Minimal portal HTTP client for A2 CLI (Basic or API key bearer). */
public final class KeelPortalHttpClient {
    private final URI base;
    private final String authHeader;
    private final HttpClient http;

    public KeelPortalHttpClient(String baseUrl, String user, String password, String apiKey) {
        this.base = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            this.authHeader = "Bearer " + apiKey.trim();
        } else {
            String pair = user + ":" + password;
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
        }
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public HttpResult get(String path) throws Exception {
        return send("GET", path, null);
    }

    public HttpResult post(String path, String json) throws Exception {
        return send("POST", path, json);
    }

    private HttpResult send(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authHeader)
                .header("X-Keel-Requested-With", "Keel")
                .header("Accept", "application/json");
        if ("GET".equals(method)) {
            b.GET();
        } else {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json));
        }
        HttpResponse<byte[]> res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new HttpResult(res.statusCode(), res.body() == null ? new byte[0] : res.body());
    }

    public record HttpResult(int status, byte[] body) {
        public JSONObject json() {
            try {
                return new JSONObject(new String(body, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }
}
