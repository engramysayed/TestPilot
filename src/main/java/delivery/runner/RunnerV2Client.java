package delivery.runner;

import keel.contract.runner.RequestSigner;
import keel.contract.runner.RunnerHeaders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * B07: engine -> portal client for Runner API v2 (runner token + HMAC; attempt / cancel-generation / fencing
 * headers once a job is claimed). Outbound only; the engine opens no port.
 */
public class RunnerV2Client {
    public record Response(int status, byte[] body) {
        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public record Fence(String jobId, String attemptId, long cancelGeneration, long fencingToken) {
    }

    private final URI portal;
    private final String token;
    private final HttpClient http;

    public RunnerV2Client(URI portal, String token) {
        this(portal, token, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    RunnerV2Client(URI portal, String token, HttpClient http) {
        this.portal = portal;
        this.token = token;
        this.http = http;
    }

    public Response send(String method, String path, String query, byte[] body, Fence fence) throws Exception {
        byte[] bytes = body == null ? new byte[0] : body;
        long ts = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String url = portal + path + (query == null || query.isBlank() ? "" : "?" + query);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("X-Keel-Requested-With", "Keel")
                .header(RunnerHeaders.TIMESTAMP, String.valueOf(ts))
                .header(RunnerHeaders.NONCE, nonce)
                .header(RunnerHeaders.SIGNATURE, RequestSigner.sign(token, method, path,
                        query == null || query.isBlank() ? null : query, ts, nonce, bytes));
        if (fence != null) {
            b.header(RunnerHeaders.ATTEMPT, fence.attemptId())
                    .header(RunnerHeaders.CANCEL_GENERATION, String.valueOf(fence.cancelGeneration()))
                    .header(RunnerHeaders.FENCING_TOKEN, String.valueOf(fence.fencingToken()));
        }
        if ("GET".equals(method)) {
            b.GET();
        } else {
            b.header("Content-Type", "PUT".equals(method) ? "application/octet-stream" : "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        }
        HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Response(r.statusCode(), r.body());
    }
}
