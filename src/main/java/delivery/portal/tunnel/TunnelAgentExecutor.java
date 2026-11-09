package delivery.portal.tunnel;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Local HTTP client used by {@code keel tunnel start} to satisfy relay polls. */
public final class TunnelAgentExecutor {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String portalBase;
    private final String bearerOrEmpty;

    public TunnelAgentExecutor(String portalBase, String bearerOrEmpty) {
        this.portalBase = trimSlash(portalBase);
        this.bearerOrEmpty = bearerOrEmpty == null ? "" : bearerOrEmpty;
    }

    public void runLoop(String projectId, String token, String agentSecret, String listenUrl) throws Exception {
        long backoffMs = 1_000;
        while (!Thread.currentThread().isInterrupted()) {
            JSONObject pollBody = new JSONObject()
                    .put("token", token)
                    .put("agentSecret", agentSecret)
                    .put("projectId", projectId)
                    .put("waitMs", 25_000);
            HttpResponse<String> poll;
            try {
                poll = post("/api/tunnel/agent/poll", pollBody.toString());
            } catch (java.io.IOException e) {
                // Portal restart / network blip: keep the agent alive and retry with backoff.
                System.err.println("tunnel poll failed: " + e.getMessage() + " (retry in " + backoffMs + "ms)");
                Thread.sleep(backoffMs);
                backoffMs = Math.min(30_000, backoffMs * 2);
                continue;
            }
            if (poll.statusCode() == 401) {
                throw new IllegalStateException("Tunnel credentials rejected (re-enrolled elsewhere?). "
                        + "Run: keel tunnel start --re-enroll");
            }
            if (poll.statusCode() == 204 || poll.body() == null || poll.body().isBlank()) {
                backoffMs = 1_000;
                continue;
            }
            if (poll.statusCode() != 200) {
                Thread.sleep(backoffMs);
                backoffMs = Math.min(30_000, backoffMs * 2);
                continue;
            }
            backoffMs = 1_000;
            JSONObject req = new JSONObject(poll.body());
            String requestId = req.optString("requestId", "");
            if (requestId.isBlank()) {
                continue;
            }
            String method = req.optString("method", "GET");
            String path = req.optString("path", "/");
            Map<String, Object> headers = readHeaders(req);
            byte[] body = decode(req.optString("bodyBase64", ""));
            RelayResult local;
            try {
                local = forward(listenUrl, method, path, headers, body);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                // Local app down / bad header: answer 502 instead of killing the agent loop.
                byte[] msg = ("Tunnel agent could not reach local target: " + e.getMessage())
                        .getBytes(StandardCharsets.UTF_8);
                local = new RelayResult(502, msg, Map.of("Content-Type", java.util.List.of("text/plain; charset=utf-8")));
            }
            JSONObject complete = new JSONObject()
                    .put("token", token)
                    .put("agentSecret", agentSecret)
                    .put("projectId", projectId)
                    .put("requestId", requestId)
                    .put("status", local.status())
                    .put("bodyBase64", Base64.getEncoder().encodeToString(local.body()));
            if (!local.headers().isEmpty()) {
                JSONObject single = new JSONObject();
                JSONObject multi = new JSONObject();
                local.headers().forEach((k, vs) -> {
                    if (vs != null && !vs.isEmpty()) {
                        single.put(k, vs.get(0)); // legacy portals
                        multi.put(k, new org.json.JSONArray(vs));
                    }
                });
                complete.put("headers", single);
                complete.put("multiHeaders", multi);
            }
            try {
                post("/api/tunnel/agent/complete", complete.toString());
            } catch (java.io.IOException e) {
                System.err.println("tunnel complete failed for " + requestId + ": " + e.getMessage());
            }
        }
    }

    private RelayResult forward(
            String listenUrl,
            String method,
            String path,
            Map<String, Object> headers,
            byte[] body
    ) throws Exception {
        String base = trimSlash(listenUrl);
        String p = path == null || path.isBlank() ? "/" : (path.startsWith("/") ? path : "/" + path);
        URI uri = URI.create(base + p);
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(75));
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String k = e.getKey();
            if (isRestrictedRequestHeader(k)) {
                continue;
            }
            b.header(k, String.valueOf(e.getValue()));
        }
        HttpRequest req = switch (method == null ? "GET" : method.toUpperCase()) {
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            case "PUT" -> b.PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            case "PATCH" -> b.method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body)).build();
            case "DELETE" -> b.DELETE().build();
            case "HEAD" -> b.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            default -> b.GET().build();
        };
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        Map<String, java.util.List<String>> outHeaders = new LinkedHashMap<>();
        res.headers().map().forEach((k, v) -> {
            if (!v.isEmpty() && !isHopByHopHeader(k) && !k.startsWith(":")) {
                outHeaders.put(k, java.util.List.copyOf(v));
            }
        });
        return new RelayResult(res.statusCode(), res.body() == null ? new byte[0] : res.body(), outHeaders);
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(portalBase + path))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("X-Keel-Requested-With", "Keel")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (!bearerOrEmpty.isBlank()) {
            boolean hasScheme = bearerOrEmpty.startsWith("Bearer ") || bearerOrEmpty.startsWith("Basic ");
            b.header("Authorization", hasScheme ? bearerOrEmpty : "Bearer " + bearerOrEmpty);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> readHeaders(JSONObject req) {
        JSONObject headersObj = req.optJSONObject("headers");
        if (headersObj == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : headersObj.keySet()) {
            Object v = headersObj.get(key);
            if (v != null) {
                out.put(key, String.valueOf(v));
            }
        }
        return out;
    }

    private static byte[] decode(String b64) {
        if (b64 == null || b64.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(b64);
    }

    /** Headers java.net.http rejects (IllegalArgumentException) or that must not cross a proxy hop. */
    private static boolean isRestrictedRequestHeader(String name) {
        String k = name.toLowerCase(Locale.ROOT);
        return k.equals("host") || k.equals("content-length") || k.equals("expect")
                || isHopByHopHeader(k);
    }

    private static boolean isHopByHopHeader(String name) {
        if (name == null) {
            return true;
        }
        String k = name.toLowerCase(Locale.ROOT);
        return k.equals("connection") || k.equals("transfer-encoding") || k.equals("keep-alive")
                || k.equals("proxy-authenticate") || k.equals("proxy-authorization")
                || k.equals("te") || k.equals("trailer") || k.equals("upgrade");
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private record RelayResult(int status, byte[] body, Map<String, java.util.List<String>> headers) {
    }
}
