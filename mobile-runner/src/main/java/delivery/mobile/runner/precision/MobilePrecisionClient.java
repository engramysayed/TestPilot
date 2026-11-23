package delivery.mobile.runner.precision;

import delivery.mobile.runner.agent.MobileCaseRunner;
import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.openqa.selenium.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 7b: runner-side caller for mobile Precision. When
 * {@link MobileCaseRunner#bind} ends with more than one actionable text-contains hit, the
 * runner delegates the pick to this client. We POST
 * {@code {intent, candidates[id=nodeIndex, label, contextKind, userOverride], stepContext,
 * snapshotExcerpt}} to the portal's
 * {@code /api/v1/runners/mobile/runs/{runId}/precision/bind} endpoint and read back the gate
 * verdict; the portal is the one that invokes Cursor (runners don't ship Node / API keys).
 *
 * <p>Any non-success (transport error, non-200, invalid id, provider fallback) returns
 * {@link Optional#empty()}, which {@link MobileCaseRunner#bind} treats as the pre-Precision
 * {@code element not found}. Failures log to {@code System.err} so a dead portal or a bad
 * sidecar leaves a breadcrumb. Design in {@code docs/MOBILE_FRAMEWORK_RULES.md} §5.
 *
 * <p>Per-run cache: the same (intent, candidate-index-set) is cached so a retried step
 * (or two cases hitting the same ambiguous widget) does not pay a second sidecar call. The
 * cache lives for the life of the client — one per run.
 */
public final class MobilePrecisionClient implements MobileCaseRunner.PrecisionBinder {

    private static final Json JSON = new Json();
    /** Match {@code MobilePrecisionBindService.MAX_CANDIDATES} so we don't ship rows the service will drop. */
    static final int MAX_CANDIDATES = 25;

    private final HttpClient http;
    private final String portal;
    private final String token;
    private final String runId;
    /** (intent | sorted,candidate,ids) → chosen node index. Stable within one runner lifetime. */
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    public MobilePrecisionClient(HttpClient http, String portal, String token, String runId) {
        this.http = http;
        this.portal = portal == null ? "" : (portal.endsWith("/") ? portal.substring(0, portal.length() - 1) : portal);
        this.token = token == null ? "" : token;
        this.runId = runId == null ? "" : runId;
    }

    @Override
    public Optional<Integer> pick(String intent, List<MobileSnapshotNode> candidates) {
        if (portal.isEmpty() || token.isEmpty() || runId.isEmpty()) return Optional.empty();
        if (candidates == null || candidates.size() < 2) return Optional.empty();
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = candidates.subList(0, MAX_CANDIDATES);
        }

        String cacheKey = cacheKey(intent, candidates);
        Integer cached = cache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        List<Map<String, Object>> rows = new ArrayList<>(candidates.size());
        for (MobileSnapshotNode n : candidates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", Integer.toString(n.index()));
            row.put("label", label(n));
            row.put("contextKind", "NATIVE");
            row.put("userOverride", false);
            rows.add(row);
        }
        String body = JSON.toJson(Map.of(
                "intent", intent == null ? "" : intent,
                "candidates", rows,
                "stepContext", "NATIVE",
                "snapshotExcerpt", buildExcerpt(candidates)));
        HttpResponse<String> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(portal + "/api/v1/runners/mobile/runs/" + runId + "/precision/bind"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("X-Keel-Requested-With", "Keel")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            System.err.println("mobile precision: portal unreachable for run " + runId + ": " + e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (res.statusCode() != 200 || res.body() == null || res.body().isBlank()) {
            System.err.println("mobile precision: bind returned HTTP " + res.statusCode()
                    + " for run " + runId);
            return Optional.empty();
        }
        Map<String, Object> out;
        try {
            out = JSON.toType(res.body(), Json.MAP_TYPE);
        } catch (RuntimeException e) {
            System.err.println("mobile precision: bind body is not JSON for run " + runId + ": " + e.getMessage());
            return Optional.empty();
        }
        String code = str(out.get("code"));
        String chosen = str(out.get("chosenId"));
        String fallback = str(out.get("fallbackReason"));
        if (!code.isEmpty() || chosen.isEmpty()) {
            if (!fallback.isEmpty()) {
                System.err.println("mobile precision: fallback (" + fallback + ") for run " + runId
                        + "; code=" + code);
            }
            return Optional.empty();
        }
        try {
            int picked = Integer.parseInt(chosen);
            cache.put(cacheKey, picked);
            return Optional.of(picked);
        } catch (NumberFormatException e) {
            System.err.println("mobile precision: non-integer chosenId '" + chosen + "' for run " + runId);
            return Optional.empty();
        }
    }

    private static String cacheKey(String intent, List<MobileSnapshotNode> candidates) {
        String ids = candidates.stream()
                .map(n -> Integer.toString(n.index()))
                .sorted()
                .collect(Collectors.joining(","));
        return (intent == null ? "" : intent) + "|" + ids;
    }

    /** Small XML-ish excerpt of just the candidates so the sidecar sees class/bounds too. */
    static String buildExcerpt(List<MobileSnapshotNode> candidates) {
        StringBuilder sb = new StringBuilder(64 + candidates.size() * 128);
        sb.append("<nodes>\n");
        for (MobileSnapshotNode n : candidates) {
            sb.append("  <node index=\"").append(n.index()).append("\"");
            sb.append(" class=\"").append(xml(n.className())).append("\"");
            MobileBounds b = n.bounds();
            if (b != null) {
                sb.append(" bounds=\"[").append(b.left()).append(',').append(b.top())
                        .append("][").append(b.right()).append(',').append(b.bottom()).append("]\"");
            }
            if (!n.resourceId().isBlank()) sb.append(" resource-id=\"").append(xml(n.resourceId())).append("\"");
            if (!n.contentDesc().isBlank()) sb.append(" content-desc=\"").append(xml(n.contentDesc())).append("\"");
            sb.append(n.clickable() ? " clickable=\"true\"" : "");
            sb.append(">").append(xml(n.text())).append("</node>\n");
        }
        sb.append("</nodes>");
        return sb.toString();
    }

    private static String xml(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String label(MobileSnapshotNode n) {
        if (!n.text().isBlank()) return n.text();
        if (!n.contentDesc().isBlank()) return n.contentDesc();
        return n.resourceId();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
