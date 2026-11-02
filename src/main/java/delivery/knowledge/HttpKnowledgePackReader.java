package delivery.knowledge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * KnowledgePackReader against Backend GET /api/projects/{projectId}/knowledge-packs[/{packId}].
 *
 * <p>Production (same-process portal): use {@link #local(ListFn, GetFn)} with
 * {@code KnowledgePackService} so HuntWorker does not HTTP-loopback into servlet security.
 * That service is the same store the live GET controllers expose — not a second FS store.
 *
 * <p>Remote/out-of-process: use {@link #http(String)} or {@link #HttpKnowledgePackReader(String)}.
 * Missing packs / transport errors log and skip (no hard-fail of the whole hunt).
 */
public final class HttpKnowledgePackReader implements KnowledgePackReader {
    private static final Logger log = LogManager.getLogger(HttpKnowledgePackReader.class);

    @FunctionalInterface
    public interface ListFn {
        List<KnowledgePack> list(String projectId) throws Exception;
    }

    @FunctionalInterface
    public interface GetFn {
        Optional<KnowledgePack> get(String projectId, String packId) throws Exception;
    }

    private final String apiBaseUrl;
    private final ListFn listFn;
    private final GetFn getFn;

    /** Remote HTTP client against a portal base URL (e.g. {@code http://localhost:8080}). */
    public HttpKnowledgePackReader(String apiBaseUrl) {
        HttpKnowledgePackReader built = http(apiBaseUrl);
        this.apiBaseUrl = built.apiBaseUrl;
        this.listFn = built.listFn;
        this.getFn = built.getFn;
    }

    private HttpKnowledgePackReader(String apiBaseUrl, ListFn listFn, GetFn getFn) {
        this.apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        this.listFn = listFn;
        this.getFn = getFn;
    }

    /**
     * Same-process: call the portal service that backs the live GET APIs.
     * Prefer this inside keel-portal so Hunt jobs do not re-enter servlet auth.
     */
    public static HttpKnowledgePackReader local(ListFn listFn, GetFn getFn) {
        if (listFn == null || getFn == null) {
            throw new IllegalArgumentException("listFn/getFn required");
        }
        return new HttpKnowledgePackReader("local", listFn, getFn);
    }

    /** Out-of-process HTTP against {@code apiBaseUrl}. */
    public static HttpKnowledgePackReader http(String apiBaseUrl) {
        String base = trimTrailingSlash(apiBaseUrl == null ? "" : apiBaseUrl.trim());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new HttpKnowledgePackReader(base,
                projectId -> fetchList(client, base, projectId),
                (projectId, packId) -> fetchOne(client, base, projectId, packId));
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    public boolean isLocalTransport() {
        return "local".equals(apiBaseUrl);
    }

    @Override
    public List<KnowledgePack> list(String projectId) {
        try {
            List<KnowledgePack> out = listFn.list(projectId);
            return out == null ? List.of() : List.copyOf(out);
        } catch (Exception e) {
            log.warn("KnowledgePack list failed projectId={} base={}: {}",
                    projectId, apiBaseUrl, e.toString());
            return List.of();
        }
    }

    @Override
    public Optional<KnowledgePack> get(String projectId, String packId) {
        if (packId == null || packId.isBlank()) {
            return Optional.empty();
        }
        String id = packId.trim();
        try {
            Optional<KnowledgePack> found = getFn.get(projectId, id);
            return found == null ? Optional.empty() : found;
        } catch (Exception e) {
            log.warn("KnowledgePack get failed projectId={} packId={} base={}: {}",
                    projectId, id, apiBaseUrl, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public List<KnowledgePack> getByIds(String projectId, List<String> packIds) {
        if (packIds == null || packIds.isEmpty()) {
            return List.of();
        }
        ArrayList<KnowledgePack> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : packIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String id = raw.trim();
            if (!seen.add(id)) {
                continue;
            }
            Optional<KnowledgePack> pack = get(projectId, id);
            if (pack.isPresent()) {
                out.add(pack.get());
            } else {
                log.warn("KnowledgePack missing or unreadable; skipping packId={} projectId={}",
                        id, projectId);
            }
        }
        return List.copyOf(out);
    }

    private static List<KnowledgePack> fetchList(HttpClient client, String base, String projectId)
            throws Exception {
        if (base.isBlank()) {
            return List.of();
        }
        String path = "/api/projects/" + enc(projectId) + "/knowledge-packs";
        HttpResponse<String> resp = sendGet(client, base + path);
        int status = resp.statusCode();
        if (status == 404) {
            return List.of();
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + " for " + path);
        }
        String body = resp.body() == null ? "[]" : resp.body().trim();
        if (body.isEmpty()) {
            return List.of();
        }
        JSONArray arr = new JSONArray(body);
        ArrayList<KnowledgePack> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            out.add(fromJson(arr.getJSONObject(i), false));
        }
        return List.copyOf(out);
    }

    private static Optional<KnowledgePack> fetchOne(
            HttpClient client, String base, String projectId, String packId) throws Exception {
        if (base.isBlank()) {
            return Optional.empty();
        }
        String path = "/api/projects/" + enc(projectId) + "/knowledge-packs/" + enc(packId);
        HttpResponse<String> resp = sendGet(client, base + path);
        int status = resp.statusCode();
        if (status == 404) {
            return Optional.empty();
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + " for " + path);
        }
        String body = resp.body() == null ? "" : resp.body().trim();
        if (body.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromJson(new JSONObject(body), true));
    }

    private static HttpResponse<String> sendGet(HttpClient client, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-Keel-Requested-With", "Keel")
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static KnowledgePack fromJson(JSONObject o, boolean requireMarkdown) {
        String markdown = o.optString("markdown", "");
        if (!requireMarkdown && markdown == null) {
            markdown = "";
        }
        return new KnowledgePack(
                o.optString("packId", ""),
                o.optString("projectId", ""),
                o.optString("title", ""),
                KnowledgePackSource.parse(o.optString("source", "LIBRARY")),
                o.optInt("batchSize", 30),
                o.optInt("revision", 1),
                markdown,
                o.optString("markdownPath", ""),
                o.optString("createdAt", ""),
                o.optString("updatedAt", "")
        );
    }

    private static String enc(String raw) {
        String s = raw == null ? "" : raw.trim();
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
