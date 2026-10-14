package delivery.runner;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import keel.contract.runner.WireJobKind;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * B07: EngineAgent end to end over real HTTP against the portal (random test port; production uses 8080):
 * claim (kind-filtered) -> input -> worker -> chunked artifact upload -> fenced result.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:engine-agent-v2;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-engine-agent",
        "delivery.work-dir=./target/test-delivery-work-engine-agent",
        "keel.runner.v2.chunk-dir=./target/test-engine-agent-chunks"
})
public class EngineAgentV2Test extends AbstractTestNGSpringContextTests {
    @LocalServerPort int port;
    @Autowired PortalStore store;
    @Autowired GeneratedWorkbookService workbooks;
    final HttpClient http = HttpClient.newHttpClient();

    private JSONObject call(String method, String path, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Keel-Requested-With", "Keel").header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        Assert.assertTrue(r.statusCode() < 300, path + " -> " + r.statusCode() + " " + r.body());
        return new JSONObject(r.body());
    }

    private Map<String, String> admin() {
        return Map.of("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "admin@testpilot.local:ChangeMeAdmin1!".getBytes(StandardCharsets.UTF_8)));
    }

    private EngineAgent agent(String token, Path work, EngineAgent.Worker worker) {
        EngineConfig cfg = EngineConfig.from(new String[]{"--keel.engine.portal-url=http://localhost:" + port,
                "--keel.engine.work-dir=" + work}, Map.of("KEEL_RUNNER_TOKEN", token));
        return new EngineAgent(cfg, new RunnerV2Client(cfg.portalUrl(), cfg.token()), worker);
    }

    @Test
    public void engineRunsAnExecuteJobThroughV2() throws Exception {
        String projectId = call("POST", "/api/projects", "{\"name\":\"Engine\",\"baseUrl\":\"https://staging.example/\"}", admin())
                .getString("projectId");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "engine");
        String token = call("POST", "/api/projects/" + projectId + "/runners", "{\"label\":\"engine\"}", admin()).getString("token");
        String svc = call("POST", "/api/projects/" + projectId + "/services", "{\"label\":\"ci\",\"role\":\"ADMIN\"}", admin()).getString("token");
        Path work = Files.createTempDirectory("engine-agent");

        // kind filter: an engine that only takes CONVERT doesn't see the EXECUTE job
        String jobId = call("POST", "/api/v1/projects/" + projectId + "/jobs", "{\"kind\":\"EXECUTE\",\"runner\":\"private\"}",
                Map.of("Authorization", "Bearer " + svc)).getString("jobId");
        RunnerV2Client raw = new RunnerV2Client(URI.create("http://localhost:" + port), token);
        Assert.assertEquals(raw.send("POST", "/api/v2/runners/claim", null,
                "{\"kinds\":[\"CONVERT\"]}".getBytes(StandardCharsets.UTF_8), null).status(), 204);

        AtomicReference<WireJobKind> seen = new AtomicReference<>();
        EngineAgent agent = agent(token, work, (kind, request, cancel) -> {
            seen.set(kind);
            Assert.assertEquals(request.jobId(), jobId);
            Assert.assertTrue(Files.isRegularFile(request.excel()), "frozen input unpacked");
            Assert.assertFalse(cancel.getAsBoolean());
            Files.writeString(request.workDir().resolve("report.txt"), "x".repeat(EngineAgent.CHUNK + 10));
            return new EngineAgent.Outcome("COMPLETED", 1, 0, "engine ok");
        });
        Assert.assertEquals(agent.runOnce(), 1);
        Assert.assertEquals(seen.get(), WireJobKind.EXECUTE);
        JobRecord job = store.getJob(jobId).orElseThrow();
        Assert.assertEquals(job.getStatus(), JobRecord.Status.COMPLETED);
        Assert.assertEquals(job.getMessage(), "engine ok");
        Assert.assertNotNull(job.getZipPath());
        Assert.assertTrue(Files.size(job.getZipPath()) > 0, "multi-chunk artifact stored");
        Assert.assertEquals(agent.runOnce(), 0, "nothing left to claim");
    }

    @Test
    public void workerFailureIsReportedAsFailedNotLost() throws Exception {
        String projectId = call("POST", "/api/projects", "{\"name\":\"Engine fail\",\"baseUrl\":\"https://staging.example/\"}", admin())
                .getString("projectId");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "engine");
        String token = call("POST", "/api/projects/" + projectId + "/runners", "{\"label\":\"engine\"}", admin()).getString("token");
        String svc = call("POST", "/api/projects/" + projectId + "/services", "{\"label\":\"ci\",\"role\":\"ADMIN\"}", admin()).getString("token");
        String jobId = call("POST", "/api/v1/projects/" + projectId + "/jobs", "{\"kind\":\"EXECUTE\",\"runner\":\"private\"}",
                Map.of("Authorization", "Bearer " + svc)).getString("jobId");
        EngineAgent agent = agent(token, Files.createTempDirectory("engine-agent-fail"), (k, r, c) -> {
            throw new IllegalStateException("chrome missing");
        });
        Assert.assertEquals(agent.runOnce(), 1);
        JobRecord job = store.getJob(jobId).orElseThrow();
        Assert.assertEquals(job.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(job.getMessage(), "chrome missing");
    }
}
