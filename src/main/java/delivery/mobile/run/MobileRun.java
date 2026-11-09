package delivery.mobile.run;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One mobile Execute run: reserve device → start Appium → runner executes → release. */
public final class MobileRun {

    public enum Status {
        QUEUED,            // accepted, device not reserved yet
        STARTING,          // lease reserved, Appium starting
        WAITING_FOR_RUNNER,// Appium session up, waiting for a mobile runner to claim
        RUNNING,           // runner claimed and heartbeats
        COMPLETED,
        FAILED,
        CANCELLED;

        public boolean terminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED;
        }
    }

    /** One case as the runner needs it (lines are already split). */
    public record CaseInput(String tcId, String title, List<String> steps, List<String> expected, List<String> testData) {
    }

    final String runId;
    final String tenantId;
    final String projectId;
    final Long ownerUserId;
    final String deviceId;
    final List<CaseInput> cases;
    final String appPackage;
    final String appActivity;
    final Instant createdAt = Instant.now();

    volatile Status status = Status.QUEUED;
    volatile String message = "Queued";
    volatile String error = "";
    volatile String leaseId = "";
    volatile long fenceToken = -1L;
    volatile String appiumUrl = "";
    volatile String sessionId = "";
    volatile String runnerId = "";
    volatile Instant claimedAt;
    volatile Instant lastRunnerHeartbeat;
    volatile Instant finishedAt;
    volatile boolean cancelRequested;
    final List<JSONObject> results = new ArrayList<>();

    MobileRun(String runId, String tenantId, String projectId, Long ownerUserId, String deviceId,
              List<CaseInput> cases, String appPackage, String appActivity) {
        this.runId = runId;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.ownerUserId = ownerUserId;
        this.deviceId = deviceId;
        this.cases = List.copyOf(cases);
        this.appPackage = appPackage == null ? "" : appPackage;
        this.appActivity = appActivity == null ? "" : appActivity;
    }

    public String runId() {
        return runId;
    }

    public String tenantId() {
        return tenantId;
    }

    public Status status() {
        return status;
    }

    /** Public view (no Appium URL / session id). */
    public JSONObject view() {
        JSONObject o = new JSONObject();
        o.put("runId", runId);
        o.put("projectId", projectId);
        o.put("deviceId", deviceId);
        o.put("status", status.name());
        o.put("message", message);
        o.put("error", error);
        o.put("createdAt", createdAt.toString());
        o.put("claimedAt", claimedAt == null ? "" : claimedAt.toString());
        o.put("finishedAt", finishedAt == null ? "" : finishedAt.toString());
        o.put("caseCount", cases.size());
        JSONArray r = new JSONArray();
        synchronized (results) {
            results.forEach(r::put);
        }
        o.put("results", r);
        long passed = 0;
        long failed = 0;
        synchronized (results) {
            for (JSONObject x : results) {
                String out = x.optString("outcome", "");
                if ("PASSED".equals(out)) passed++;
                else if ("FAILED".equals(out) || "BLOCKED".equals(out) || "INTERRUPTED_UNCERTAIN".equals(out)) failed++;
            }
        }
        o.put("passed", passed);
        o.put("failed", failed);
        return o;
    }

    /** What the runner receives on claim. */
    JSONObject claimPayload() {
        JSONObject o = new JSONObject();
        o.put("runId", runId);
        o.put("appiumUrl", appiumUrl);
        o.put("sessionId", sessionId);
        o.put("appPackage", appPackage);
        o.put("appActivity", appActivity);
        JSONArray cs = new JSONArray();
        for (CaseInput c : cases) {
            cs.put(new JSONObject()
                    .put("tcId", c.tcId())
                    .put("title", c.title())
                    .put("steps", new JSONArray(c.steps()))
                    .put("expected", new JSONArray(c.expected()))
                    .put("testData", new JSONArray(c.testData())));
        }
        o.put("cases", cs);
        return o;
    }
}
