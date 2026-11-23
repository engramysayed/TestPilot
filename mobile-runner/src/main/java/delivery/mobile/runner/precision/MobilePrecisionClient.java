package delivery.mobile.runner.precision;

import delivery.mobile.runner.agent.MobileCaseRunner;
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
 * {@code element not found}. Design in {@code docs/MOBILE_FRAMEWORK_RULES.md} §5.
 */
public final class MobilePrecisionClient implements MobileCaseRunner.PrecisionBinder {

    private static final Json JSON = new Json();

    private final HttpClient http;
    private final String portal;
    private final String token;
    private final String runId;

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
                "snapshotExcerpt", ""));
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
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (res.statusCode() != 200 || res.body() == null || res.body().isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> out;
        try {
            out = JSON.toType(res.body(), Json.MAP_TYPE);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String code = str(out.get("code"));
        String chosen = str(out.get("chosenId"));
        if (!code.isEmpty() || chosen.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(chosen));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
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
