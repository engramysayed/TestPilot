package delivery.runner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3 AE schedule hook: {@code GET /api/schedules/due} then
 * {@code POST /api/projects/{projectId}/suites/{suiteId}/runs}.
 * Auth: Bearer runner token (same as engine). Backend owns due calc + run create.
 */
public final class ScheduleClaimClient {
    public record DueItem(
            String projectId,
            String suiteId,
            JSONObject schedule,
            List<String> tcIds,
            String alertChannelId,
            String monitorTarget
    ) {
        public DueItem {
            tcIds = tcIds == null ? List.of() : List.copyOf(tcIds);
        }
    }

    public record StartResult(String projectId, String suiteId, String jobId, int httpStatus, String body) {
    }

    private final URI portal;
    private final String token;
    private final HttpClient http;

    public ScheduleClaimClient(URI portal, String token) {
        this(portal, token, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    ScheduleClaimClient(URI portal, String token, HttpClient http) {
        this.portal = portal;
        this.token = token;
        this.http = http;
    }

    public List<DueItem> listDue(Integer withinSeconds) throws Exception {
        String query = withinSeconds == null ? "" : ("withinSeconds=" + withinSeconds);
        HttpResponse<String> r = send("GET", "/api/schedules/due", query, null);
        if (r.statusCode() == 404 || r.statusCode() == 501) {
            return List.of();
        }
        if (r.statusCode() < 200 || r.statusCode() >= 300) {
            throw new IllegalStateException("schedules/due HTTP " + r.statusCode() + ": " + trim(r.body()));
        }
        String body = r.body() == null ? "" : r.body().trim();
        if (body.isEmpty()) {
            return List.of();
        }
        JSONArray arr;
        if (body.startsWith("[")) {
            arr = new JSONArray(body);
        } else {
            JSONObject wrap = new JSONObject(body);
            arr = wrap.optJSONArray("items");
            if (arr == null) {
                arr = wrap.optJSONArray("due");
            }
            if (arr == null) {
                return List.of();
            }
        }
        List<DueItem> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String projectId = o.optString("projectId", "").trim();
            String suiteId = o.optString("suiteId", "").trim();
            if (projectId.isEmpty() || suiteId.isEmpty()) {
                continue;
            }
            List<String> tcIds = new ArrayList<>();
            JSONArray tc = o.optJSONArray("tcIds");
            if (tc != null) {
                for (int j = 0; j < tc.length(); j++) {
                    String id = tc.optString(j, "").trim();
                    if (!id.isEmpty()) {
                        tcIds.add(id);
                    }
                }
            }
            String alert = o.optString("alertChannelId", null);
            if (alert != null && (alert.isBlank() || "null".equalsIgnoreCase(alert))) {
                alert = null;
            }
            String monitorTarget = null;
            JSONObject monitor = o.optJSONObject("monitor");
            if (monitor != null && monitor.optBoolean("enabled", false)) {
                monitorTarget = monitor.optString("target", null);
            }
            out.add(new DueItem(projectId, suiteId, o.optJSONObject("schedule"), tcIds, alert, monitorTarget));
        }
        return out;
    }

    public StartResult startRun(DueItem item, String trigger) throws Exception {
        if (item == null) {
            throw new IllegalArgumentException("due item required");
        }
        String t = trigger == null || trigger.isBlank() ? "schedule" : trigger.trim();
        JSONObject body = new JSONObject();
        body.put("trigger", t);
        if (item.tcIds() != null && !item.tcIds().isEmpty()) {
            body.put("tcIds", new JSONArray(item.tcIds()));
        }
        if (item.alertChannelId() != null) {
            body.put("alertChannelId", item.alertChannelId());
        }
        String path = "/api/projects/" + item.projectId() + "/suites/" + item.suiteId() + "/runs";
        HttpResponse<String> r = send("POST", path, null, body.toString());
        String jobId = null;
        String resp = r.body() == null ? "" : r.body();
        try {
            JSONObject o = new JSONObject(resp);
            jobId = o.optString("jobId", o.optString("id", null));
            if (jobId != null && jobId.isBlank()) {
                jobId = null;
            }
        } catch (Exception ignored) {
            // non-JSON body
        }
        return new StartResult(item.projectId(), item.suiteId(), jobId, r.statusCode(), resp);
    }

    /**
     * Poll due schedules and start each run. Activates {@link delivery.job.SuiteRunContext} per item
     * for in-process callers; remote portal workers receive filter via POST body (Backend wires).
     *
     * @return number of successful starts (2xx)
     */
    public int claimAndStartDue(Integer withinSeconds) throws Exception {
        List<DueItem> due = listDue(withinSeconds);
        int started = 0;
        for (DueItem item : due) {
            String trigger = "schedule";
            if (item.monitorTarget() != null && !item.monitorTarget().isBlank()) {
                // monitor-enabled suites still use schedule machinery; tag trigger=monitor when target present
                // only if schedule object says so — default schedule
            }
            JSONObject sched = item.schedule();
            if (sched != null && "monitor".equalsIgnoreCase(sched.optString("trigger", ""))) {
                trigger = "monitor";
            }
            delivery.job.SuiteRunContext.activate(new delivery.job.SuiteRunContext(
                    item.projectId(),
                    item.suiteId(),
                    null,
                    item.tcIds(),
                    trigger,
                    item.alertChannelId(),
                    item.monitorTarget()
            ));
            try {
                StartResult res = startRun(item, trigger);
                if (res.httpStatus() >= 200 && res.httpStatus() < 300) {
                    started++;
                    System.out.println("SCHEDULE_CLAIM: started suite=" + item.suiteId()
                            + " project=" + item.projectId() + " jobId=" + res.jobId());
                } else if (res.httpStatus() == 404 || res.httpStatus() == 501) {
                    System.out.println("SCHEDULE_CLAIM: runs endpoint not ready HTTP " + res.httpStatus());
                } else {
                    System.err.println("SCHEDULE_CLAIM: start failed HTTP " + res.httpStatus()
                            + " suite=" + item.suiteId() + " " + trim(res.body()));
                }
            } finally {
                delivery.job.SuiteRunContext.clear();
            }
        }
        return started;
    }

    private HttpResponse<String> send(String method, String path, String query, String jsonBody) throws Exception {
        String url = portal.toString() + path + (query == null || query.isBlank() ? "" : "?" + query);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("X-Keel-Requested-With", "Keel")
                .header("Accept", "application/json");
        if ("GET".equals(method)) {
            b.GET();
        } else {
            byte[] bytes = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
            b.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
