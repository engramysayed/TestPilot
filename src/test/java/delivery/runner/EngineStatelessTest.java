package delivery.runner;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * B11 (OPS §12.5 Part A item 5): the engine is stateless. Wiping its {@code work}, {@code spool} and
 * {@code chrome-profile} folders between two runs loses nothing: run 1's results are already in the portal, and
 * run 2 starts from an empty engine home and completes (dry-run workers, random test port).
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:engine-stateless;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-engine-stateless",
        "delivery.work-dir=./target/test-delivery-work-engine-stateless",
        "keel.runner.v2.chunk-dir=./target/test-engine-stateless-chunks"
})
public class EngineStatelessTest extends AbstractTestNGSpringContextTests {
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

    static void wipe(Path home) throws Exception {
        for (String d : List.of("work", "spool", "chrome-profile")) {
            Path p = home.resolve(d);
            if (Files.exists(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(x);
                }
            }
        }
    }

    @Test
    public void wipingEngineFoldersBetweenRunsLosesNothing() throws Exception {
        String projectId = call("POST", "/api/projects", "{\"name\":\"Stateless\",\"baseUrl\":\"https://staging.example/\"}", admin())
                .getString("projectId");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "stateless");
        String token = call("POST", "/api/projects/" + projectId + "/runners", "{\"label\":\"engine\"}", admin()).getString("token");
        String svc = call("POST", "/api/projects/" + projectId + "/services", "{\"label\":\"ci\",\"role\":\"ADMIN\"}", admin()).getString("token");
        Path home = Files.createTempDirectory("engine-stateless");
        EngineConfig cfg = EngineConfig.from(new String[]{"--keel.engine.portal-url=http://localhost:" + port,
                "--keel.engine.home=" + home}, Map.of("KEEL_RUNNER_TOKEN", token));
        EngineAgent.Worker worker = (kind, request, cancel) -> {
            Files.createDirectories(home.resolve("spool"));
            Files.createDirectories(home.resolve("chrome-profile"));
            Files.writeString(home.resolve("spool").resolve(request.jobId()), "spooled");
            Files.writeString(request.workDir().resolve("report.txt"), "run " + request.jobId());
            return new EngineAgent.Outcome("COMPLETED", 1, 0, "ok " + kind);
        };

        String job1 = call("POST", "/api/v1/projects/" + projectId + "/jobs", "{\"kind\":\"EXECUTE\",\"runner\":\"private\"}",
                Map.of("Authorization", "Bearer " + svc)).getString("jobId");
        Assert.assertEquals(new EngineAgent(cfg, new RunnerV2Client(cfg.portalUrl(), cfg.token()), worker).runOnce(), 1);
        JobRecord done1 = store.getJob(job1).orElseThrow();
        Assert.assertEquals(done1.getStatus(), JobRecord.Status.COMPLETED);
        Path zip1 = done1.getZipPath();
        long size1 = Files.size(zip1);

        wipe(home);
        Assert.assertFalse(Files.exists(home.resolve("work")));

        String job2 = EngineTestJobs.convertLike(store, job1);
        // a fresh agent, as after an engine restart: nothing carried over in memory either
        Assert.assertEquals(new EngineAgent(cfg, new RunnerV2Client(cfg.portalUrl(), cfg.token()), worker).runOnce(), 1);
        JobRecord done2 = store.getJob(job2).orElseThrow();
        Assert.assertEquals(done2.getStatus(), JobRecord.Status.COMPLETED, done2.getMessage());
        Assert.assertEquals(done2.getMessage(), "ok CONVERT");

        JobRecord again1 = store.getJob(job1).orElseThrow();
        Assert.assertEquals(again1.getStatus(), JobRecord.Status.COMPLETED, "run 1 untouched by the wipe");
        Assert.assertEquals(Files.size(again1.getZipPath()), size1, "run 1 evidence lives in the portal");
    }
}
