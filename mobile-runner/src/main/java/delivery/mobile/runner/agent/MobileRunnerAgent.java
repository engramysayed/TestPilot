package delivery.mobile.runner.agent;

import delivery.mobile.runner.execute.CaseOutcome;
import delivery.mobile.runner.execute.CaseResult;
import delivery.mobile.runner.execute.MobileExecuteEngine;
import delivery.mobile.runner.execute.SelectedCase;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mobile runner process. Polls the portal for a mobile run whose device session is ready, drives it through
 * {@link MobileExecuteEngine} + {@link MobileCaseRunner}, heartbeats every 10 s (the portal renews the device
 * lease only while heartbeats arrive), and posts the results; the portal then releases the device.
 *
 * <pre>
 *   set KEEL_PORTAL_URL=http://127.0.0.1:8080
 *   set KEEL_RUNNER_TOKEN=tp_run_…            (Projects → Runners → enroll)
 *   java -cp mobile-runner.jar delivery.mobile.runner.agent.MobileRunnerAgent [--once]
 * </pre>
 */
public final class MobileRunnerAgent {

    private static final Json JSON = new Json();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String portal;
    private final String token;

    public MobileRunnerAgent(String portalUrl, String runnerToken) {
        this.portal = portalUrl.endsWith("/") ? portalUrl.substring(0, portalUrl.length() - 1) : portalUrl;
        this.token = runnerToken;
    }

    public static void main(String[] args) throws Exception {
        String portal = env("KEEL_PORTAL_URL", "http://127.0.0.1:8080");
        String token = env("KEEL_RUNNER_TOKEN", "");
        if (token.isBlank()) {
            System.err.println("KEEL_RUNNER_TOKEN is required (enroll a runner on the project's Runners tab)");
            System.exit(2);
        }
        // Plain http to a non-loopback portal would send the bearer in clear text.
        try {
            java.net.URI uri = java.net.URI.create(portal);
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                String host = uri.getHost() == null ? "" : uri.getHost();
                boolean loopback = host.equals("127.0.0.1") || host.equals("localhost")
                        || host.equals("::1") || host.equals("[::1]");
                if (!loopback) {
                    System.err.println("KEEL_PORTAL_URL points off-host (" + host
                            + ") over plain http; use https.");
                    System.exit(2);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("KEEL_PORTAL_URL is not a valid URL: " + portal);
            System.exit(2);
        }
        boolean once = List.of(args).contains("--once");
        MobileRunnerAgent agent = new MobileRunnerAgent(portal, token);
        do {
            boolean worked = agent.pollOnce();
            if (!worked) {
                Thread.sleep(5_000);
            }
        } while (!once);
    }

    /** @return true when a run was claimed and processed. */
    @SuppressWarnings("unchecked")
    public boolean pollOnce() throws InterruptedException {
        HttpResponse<String> claim;
        try {
            claim = post("/api/v1/runners/mobile/claim", "{}");
        } catch (java.io.IOException e) {
            System.err.println("portal unreachable: " + e.getMessage());
            return false;
        }
        if (claim.statusCode() == 204 || claim.body() == null || claim.body().isBlank()) {
            return false;
        }
        if (claim.statusCode() != 200) {
            System.err.println("claim failed: HTTP " + claim.statusCode() + " " + claim.body());
            return false;
        }
        Map<String, Object> job = JSON.toType(claim.body(), Json.MAP_TYPE);
        String runId = String.valueOf(job.get("runId"));
        System.out.println("claimed mobile run " + runId);

        W3cAppiumDriverPort driver = new W3cAppiumDriverPort(
                String.valueOf(job.get("appiumUrl")), String.valueOf(job.get("sessionId")));
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicReference<String> progress = new AtomicReference<>("starting");
        Thread hb = new Thread(() -> {
            while (!done.get()) {
                try {
                    HttpResponse<String> r = post("/api/v1/runners/mobile/runs/" + runId + "/heartbeat",
                            JSON.toJson(Map.of("progress", progress.get())));
                    if (r.statusCode() == 200 && r.body() != null && r.body().contains("\"cancel\":true")) {
                        driver.cancel();
                    }
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    return;
                } catch (Exception ignored) {
                    // next beat
                }
            }
        }, "mobile-runner-heartbeat");
        hb.setDaemon(true);
        hb.start();

        List<Map<String, Object>> results = new ArrayList<>();
        String error = "";
        try {
            List<Map<String, Object>> cases = (List<Map<String, Object>>) job.get("cases");
            MobileCaseRunner runner = new MobileCaseRunner(driver,
                    String.valueOf(job.getOrDefault("appPackage", "")),
                    String.valueOf(job.getOrDefault("appActivity", "")));
            Map<String, MobileCaseRunner.CaseInput> byId = new LinkedHashMap<>();
            List<SelectedCase> selected = new ArrayList<>();
            for (Map<String, Object> c : cases) {
                String tcId = String.valueOf(c.get("tcId"));
                byId.put(tcId, new MobileCaseRunner.CaseInput(tcId,
                        strings(c.get("steps")), strings(c.get("expected")), strings(c.get("testData"))));
                selected.add(new SelectedCase(tcId, false));
            }
            Map<String, MobileCaseRunner.Outcome> details = new LinkedHashMap<>();
            MobileExecuteEngine.ExecuteReport report = new MobileExecuteEngine().run(selected, false, sc -> {
                progress.set("running " + sc.tcId());
                MobileCaseRunner.Outcome o = runner.run(byId.get(sc.tcId()));
                details.put(sc.tcId(), o);
                return o.outcome() == CaseOutcome.PASSED
                        ? CaseResult.passed(sc.tcId())
                        : CaseResult.failed(sc.tcId(), o.failureCode(), null);
            });
            for (CaseResult r : report.results()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tcId", r.tcId());
                m.put("outcome", r.outcome().name());
                m.put("failureCode", r.failureCode());
                MobileCaseRunner.Outcome d = details.get(r.tcId());
                if (d != null) {
                    m.put("failedStep", d.failedStep());
                    m.put("detail", d.detail());
                    m.put("log", d.log());
                }
                results.add(m);
            }
            if (report.deviceLost()) {
                error = "DEVICE_LOST";
            }
        } catch (RuntimeException e) {
            error = "RUNNER_ERROR: " + e.getMessage();
        } finally {
            done.set(true);
            hb.interrupt();
        }
        try {
            post("/api/v1/runners/mobile/runs/" + runId + "/result",
                    JSON.toJson(Map.of("results", results, "error", error)));
        } catch (java.io.IOException e) {
            System.err.println("could not post results for " + runId + ": " + e.getMessage());
        }
        System.out.println("finished mobile run " + runId + (error.isEmpty() ? "" : " (" + error + ")"));
        return true;
    }

    private HttpResponse<String> post(String path, String body) throws java.io.IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(portal + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("X-Keel-Requested-With", "Keel")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(x == null ? "" : String.valueOf(x));
            }
        }
        return out;
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v.trim();
    }
}
