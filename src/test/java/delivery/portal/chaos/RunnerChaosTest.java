package delivery.portal.chaos;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.runner.RunnerV2Client;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * B08 chaos suite over real HTTP (random test port; 8080 in production): the engine is "killed" by simply never
 * calling again, the portal "restarts" by dropping its in-memory job cache (state comes back from the DB).
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:runner-chaos;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-chaos",
        "delivery.work-dir=./target/test-delivery-work-chaos",
        "keel.runner.v2.chunk-dir=./target/test-chaos-chunks"
})
public class RunnerChaosTest extends AbstractTestNGSpringContextTests {
    @LocalServerPort int port;
    @Autowired PortalStore store;
    @Autowired GeneratedWorkbookService workbooks;
    final HttpClient http = HttpClient.newHttpClient();

    record Setup(String token, String jobId) {
    }

    private JSONObject call(String method, String path, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Keel-Requested-With", "Keel").header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        Assert.assertTrue(r.statusCode() < 300, path + " -> " + r.statusCode() + " " + r.body());
        return new JSONObject(r.body());
    }

    private Setup setup(String name) throws Exception {
        Map<String, String> admin = Map.of("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                "admin@testpilot.local:ChangeMeAdmin1!".getBytes(StandardCharsets.UTF_8)));
        String projectId = call("POST", "/api/projects", "{\"name\":\"" + name + "\",\"baseUrl\":\"https://staging.example/\"}", admin)
                .getString("projectId");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "chaos");
        String token = call("POST", "/api/projects/" + projectId + "/runners", "{\"label\":\"engine\"}", admin).getString("token");
        String svc = call("POST", "/api/projects/" + projectId + "/services", "{\"label\":\"ci\",\"role\":\"ADMIN\"}", admin).getString("token");
        String jobId = call("POST", "/api/v1/projects/" + projectId + "/jobs", "{\"kind\":\"EXECUTE\",\"runner\":\"private\"}",
                Map.of("Authorization", "Bearer " + svc)).getString("jobId");
        return new Setup(token, jobId);
    }

    private RunnerV2Client client(String token) {
        return new RunnerV2Client(URI.create("http://localhost:" + port), token);
    }

    private RunnerV2Client.Fence claim(RunnerV2Client c, String jobId) throws Exception {
        RunnerV2Client.Response r = c.send("POST", "/api/v2/runners/claim", null, "{}".getBytes(StandardCharsets.UTF_8), null);
        Assert.assertEquals(r.status(), 200, r.text());
        JSONObject j = new JSONObject(r.text());
        Assert.assertEquals(j.getString("jobId"), jobId);
        return new RunnerV2Client.Fence(jobId, j.getString("attemptId"), j.getLong("cancelGeneration"), j.getLong("fencingToken"));
    }

    /** Engine stops heartbeating: let its lease run out and let the portal's reconciler look at it. */
    private void engineKilled(String jobId) {
        JobRecord job = store.getJob(jobId).orElseThrow();
        job.setLeaseUntil(Instant.now().minusSeconds(1));
        store.syncJobPersistence(job);
        store.reconcileExpiredLeases();
    }

    private void portalRestart(String jobId) {
        ((Map<?, ?>) ReflectionTestUtils.getField(store, "jobs")).remove(jobId);
    }

    @Test
    public void engineKilledMidAdmittedIsRequeuedAndReclaimedWithNewFence() throws Exception {
        Setup s = setup("Chaos admitted");
        RunnerV2Client c = client(s.token());
        RunnerV2Client.Fence first = claim(c, s.jobId());
        engineKilled(s.jobId());
        JobRecord job = store.getJob(s.jobId()).orElseThrow();
        Assert.assertEquals(job.getStatus(), JobRecord.Status.QUEUED, "ADMITTED work is safe to retry");

        RunnerV2Client.Fence second = claim(c, s.jobId());
        Assert.assertNotEquals(second.attemptId(), first.attemptId());
        Assert.assertTrue(second.fencingToken() > first.fencingToken());
        // the zombie (first attempt) can no longer write
        RunnerV2Client.Response stale = c.send("POST", "/api/v2/runners/jobs/" + s.jobId() + "/result", null,
                "{\"status\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8), first);
        Assert.assertEquals(stale.status(), 409, stale.text());
        Assert.assertEquals(store.getJob(s.jobId()).orElseThrow().getStatus(), JobRecord.Status.RUNNING);
    }

    @Test
    public void engineKilledMidBrowserBecomesInterruptedUncertain() throws Exception {
        Setup s = setup("Chaos browser");
        RunnerV2Client c = client(s.token());
        RunnerV2Client.Fence f = claim(c, s.jobId());
        Assert.assertEquals(c.send("POST", "/api/v2/runners/jobs/" + s.jobId() + "/stage", null,
                "{\"stage\":\"BROWSER\"}".getBytes(StandardCharsets.UTF_8), f).status(), 200);
        engineKilled(s.jobId());
        JobRecord job = store.getJob(s.jobId()).orElseThrow();
        Assert.assertEquals(job.getError(), DurableJobClaim.INTERRUPTED_UNCERTAIN, "a browser may have had side effects");
        Assert.assertNotEquals(job.getStatus(), JobRecord.Status.QUEUED, "uncertain work is never silently retried");
        Assert.assertEquals(c.send("POST", "/api/v2/runners/claim", null, "{}".getBytes(StandardCharsets.UTF_8), null).status(), 204);
    }

    @Test
    public void portalRestartMidUploadResumesWithPersistedFence() throws Exception {
        Setup s = setup("Chaos upload");
        RunnerV2Client c = client(s.token());
        RunnerV2Client.Fence f = claim(c, s.jobId());
        String chunks = "/api/v2/runners/jobs/" + s.jobId() + "/artifacts/evidence/chunks/";
        Assert.assertEquals(c.send("PUT", chunks + "0", null, "part-0|".getBytes(StandardCharsets.UTF_8), f).status(), 202);

        portalRestart(s.jobId());
        Assert.assertEquals(store.currentFencingToken(s.jobId()), f.fencingToken(), "fence survives the restart (DB)");
        Assert.assertEquals(store.getJob(s.jobId()).orElseThrow().getFencingToken(), f.fencingToken(), "rehydrated from SQL");

        RunnerV2Client.Response last = c.send("PUT", chunks + "1", "last=true", "part-1".getBytes(StandardCharsets.UTF_8), f);
        Assert.assertEquals(last.status(), 202, last.text());
        Assert.assertEquals(Files.readString(store.getJob(s.jobId()).orElseThrow().getZipPath()), "part-0|part-1");
        // a guessed/forged token is still refused after the restart (no re-seeding any more)
        RunnerV2Client.Fence forged = new RunnerV2Client.Fence(f.jobId(), f.attemptId(), f.cancelGeneration(), f.fencingToken() + 5);
        Assert.assertEquals(c.send("POST", "/api/v2/runners/jobs/" + s.jobId() + "/lease", null, null, forged).status(), 409);
        Assert.assertEquals(c.send("POST", "/api/v2/runners/jobs/" + s.jobId() + "/result", null,
                "{\"status\":\"COMPLETED\",\"passedCount\":1}".getBytes(StandardCharsets.UTF_8), f).status(), 200);
    }

    @Test
    public void staleWritesAfterCompletionGet409() throws Exception {
        Setup s = setup("Chaos stale");
        RunnerV2Client c = client(s.token());
        RunnerV2Client.Fence f = claim(c, s.jobId());
        Assert.assertEquals(c.send("POST", "/api/v2/runners/jobs/" + s.jobId() + "/result", null,
                "{\"status\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8), f).status(), 200);
        for (String[] w : new String[][]{{"POST", "/result"}, {"POST", "/lease"}, {"POST", "/progress"}, {"PUT", "/artifacts/late/chunks/0"}}) {
            RunnerV2Client.Response r = c.send(w[0], "/api/v2/runners/jobs/" + s.jobId() + w[1], null,
                    "{}".getBytes(StandardCharsets.UTF_8), f);
            Assert.assertEquals(r.status(), 409, w[1] + " " + r.text());
        }
        Assert.assertEquals(store.getJob(s.jobId()).orElseThrow().getStatus(), JobRecord.Status.COMPLETED);
    }
}
