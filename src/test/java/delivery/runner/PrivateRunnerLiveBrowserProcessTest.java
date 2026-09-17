package delivery.runner;

import delivery.excel.ManualTestCase;
import delivery.job.DurableJobClaim;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E04 live validation: separate portal JVM (this test) and runner agent process,
 * dry-run disabled, real headless Chrome against controlled loopback pages.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:private-runner-live;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=false",
        "delivery.vision.grounding.enabled=false",
        "delivery.store-root=./target/test-delivery-store-private-runner-live",
        "delivery.work-dir=./target/test-delivery-work-private-runner-live"
})
@AutoConfigureMockMvc
public class PrivateRunnerLiveBrowserProcessTest extends AbstractTestNGSpringContextTests {

    static final String PROBE = "E04-LIVE-PROBE-435799f";

    @LocalServerPort
    private int port;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GeneratedWorkbookService workbooks;
    @Autowired
    private PortalStore store;

    private com.sun.net.httpserver.HttpServer server;
    private String baseUrl;

    @BeforeClass
    public void startLocalPages() throws Exception {
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("BROWSER_TYPE", "CHROME");
        System.setProperty("delivery.vision.grounding.enabled", "false");
        byte[] home = ("""
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"><title>%s</title></head>
                <body>
                  <h1>Checkout %s</h1>
                  <p>Ready to continue.</p>
                  <a id="continue-checkout" data-test="continue-checkout" href="/confirmed.html">Continue</a>
                </body></html>
                """.formatted(PROBE, PROBE)).getBytes(StandardCharsets.UTF_8);
        byte[] confirmed = ("""
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"><title>Confirmed %s</title></head>
                <body>
                  <h1>Order confirmed %s</h1>
                  <p>Thank you.</p>
                </body></html>
                """.formatted(PROBE, PROBE)).getBytes(StandardCharsets.UTF_8);
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/hang".equals(path)) {
                try {
                    Thread.sleep(25_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "/confirmed.html".equals(path) ? confirmed : home;
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterClass(alwaysRun = true)
    public void stopLocalPages() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-private-runner-live");
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test(timeOut = 240_000)
    public void liveAgentCompletesBrowserJobFromFrozenInputsAndUploadsEvidence() throws Exception {
        String projectId = createProject(baseUrl);
        seedWorkbook(projectId);
        String token = enroll(projectId);
        String svc = serviceToken(projectId);
        String jobId = submitPrivate(projectId, svc);
        Assert.assertTrue(store.getJob(jobId).orElseThrow().isRequirePrivateRunner());
        Assert.assertTrue(store.beginWork(jobId).isEmpty(), "portal must not execute private-runner jobs");
        String frozenHash = store.getJob(jobId).orElseThrow().getInputSnapshotHash();
        Assert.assertFalse(frozenHash == null || frozenHash.isBlank());

        Path work = Files.createTempDirectory("runner-live");
        Path log = work.resolve("agent.log");
        Process agent = startAgent(token, work, log);
        try {
            JobRecord.Status status = waitStatus(jobId, JobRecord.Status.COMPLETED, 180);
            Assert.assertEquals(status, JobRecord.Status.COMPLETED, agentLog(log));
        } finally {
            agent.destroy();
            agent.waitFor(10, TimeUnit.SECONDS);
        }

        JobRecord done = store.getJob(jobId).orElseThrow();
        Assert.assertTrue(done.getPassedCount() >= 1, "live browser must pass at least one case: " + done.getMessage());
        Assert.assertEquals(done.getInputSnapshotHash(), frozenHash);
        Assert.assertNotNull(done.getZipPath(), "signed artifact zip missing");
        Assert.assertTrue(Files.isRegularFile(done.getZipPath()), done.getZipPath().toString());
        String zip = zipText(done.getZipPath());
        Assert.assertTrue(zip.contains("confirmed.html"),
                "IR/evidence must show live navigation to confirmed.html:\n" + zip);
        Assert.assertTrue(zip.toLowerCase().contains(".png") || zip.contains("evidence"),
                "uploaded zip must include browser evidence:\n" + zip);

        String logs = agentLog(log);
        Assert.assertTrue(logs.contains("Starting Driver") && logs.contains("CHROME")
                        && logs.contains("DOM_POST_CLICK"),
                "agent process must log live Chrome execution:\n" + logs);
        Assert.assertFalse(portalWorkContainsJob(jobId),
                "evidence must not be produced by the portal process work dir");
        Assert.assertTrue(done.getWorkerId().startsWith("run_"), done.getWorkerId());
    }

    @Test(timeOut = 180_000)
    public void liveCancelDuringBrowserStopsCompletion() throws Exception {
        String hangUrl = baseUrl.replaceAll("/$", "") + "/hang";
        String projectId = createProject(hangUrl);
        seedWorkbook(projectId);
        String token = enroll(projectId);
        String jobId = submitPrivate(projectId, serviceToken(projectId));
        Path work = Files.createTempDirectory("runner-live-cancel");
        Path log = work.resolve("agent.log");
        Process agent = startAgent(token, work, log);
        try {
            waitStage(jobId, "BROWSER", 60);
            mockMvc.perform(post("/api/v1/jobs/" + jobId + "/cancel")
                            .header("Authorization", "Bearer " + serviceToken(projectId))
                            .header("X-Keel-Requested-With", "Keel"))
                    .andExpect(status().isAccepted());
            JobRecord.Status status = waitStatus(jobId, JobRecord.Status.CANCELLED, 90);
            Assert.assertEquals(status, JobRecord.Status.CANCELLED, agentLog(log));
        } finally {
            agent.destroy();
            agent.waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test(timeOut = 180_000)
    public void liveBrowserDisconnectIsUncertainNotRequeued() throws Exception {
        String hangUrl = baseUrl.replaceAll("/$", "") + "/hang";
        String projectId = createProject(hangUrl);
        seedWorkbook(projectId);
        String token = enroll(projectId);
        String jobId = submitPrivate(projectId, serviceToken(projectId));
        Path work = Files.createTempDirectory("runner-live-disc");
        Path log = work.resolve("agent.log");
        Process agent = startAgent(token, work, log);
        try {
            waitStage(jobId, "BROWSER", 60);
        } finally {
            agent.destroy();
            agent.waitFor(10, TimeUnit.SECONDS);
        }
        JobRecord running = store.getJob(jobId).orElseThrow();
        Assert.assertEquals(running.getClaimStage(), "BROWSER", agentLog(log));
        running.setLeaseUntil(java.time.Instant.now().minusSeconds(5));
        store.syncJobPersistence(running);
        Assert.assertTrue(store.reconcileExpiredLeases() >= 1);
        JobRecord dead = store.getJob(jobId).orElseThrow();
        Assert.assertEquals(dead.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(dead.getError(), DurableJobClaim.INTERRUPTED_UNCERTAIN);
    }

    private Process startAgent(String token, Path work, Path log) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-DBROWSER_HEADLESS=true",
                "-DBROWSER_TYPE=CHROME",
                "-DEXECUTION_TYPE=HEADLESS",
                "-Ddelivery.vision.grounding.enabled=false",
                "-cp", System.getProperty("java.class.path"),
                "delivery.runner.PrivateRunnerAgent",
                "--portal", "http://127.0.0.1:" + port,
                "--token", token,
                "--work-dir", work.toString(),
                "--dry-run", "false"
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        return pb.start();
    }

    private void waitStage(String jobId, String stage, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            last = store.getJob(jobId).map(JobRecord::getClaimStage).orElse("");
            if (stage.equals(last)) {
                return;
            }
            Thread.sleep(400);
        }
        Assert.fail("did not reach claimStage=" + stage + " last=" + last);
    }

    private JobRecord.Status waitStatus(String jobId, JobRecord.Status want, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        JobRecord.Status last = null;
        while (System.currentTimeMillis() < deadline) {
            last = store.getJob(jobId).map(JobRecord::getStatus).orElse(null);
            if (want == last) {
                return last;
            }
            Thread.sleep(400);
        }
        return last;
    }

    private boolean portalWorkContainsJob(String jobId) throws Exception {
        Path work = Path.of("./target/test-delivery-work-private-runner-live");
        if (!Files.exists(work)) {
            return false;
        }
        try (var walk = Files.walk(work)) {
            return walk.anyMatch(p -> p.getFileName().toString().contains(jobId));
        }
    }

    private static String zipText(Path zip) throws Exception {
        StringBuilder out = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                out.append(entry.getName()).append('\n');
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".html")
                        || name.endsWith(".md")) {
                    out.append(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                } else {
                    zis.readAllBytes();
                }
            }
        }
        return out.toString();
    }

    private static String agentLog(Path log) throws Exception {
        if (log == null || !Files.isRegularFile(log)) {
            return "";
        }
        return Files.readString(log);
    }

    private void seedWorkbook(String projectId) throws Exception {
        workbooks.saveFromCases(projectId, List.of(new ManualTestCase(
                "TC_01",
                "Continue " + PROBE + " checkout",
                "",
                "1. Click Continue",
                "Thank you.",
                "P1",
                "smoke",
                "",
                PROBE,
                "AUTOMATE"
        )), "test", "e04-live");
    }

    private String enroll(String projectId) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"live\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).getString("token");
    }

    private String serviceToken(String projectId) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).getString("token");
    }

    private String submitPrivate(String projectId, String svc) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + svc)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\",\"runner\":\"private\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).getString("jobId");
    }

    private String createProject(String origin) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"E04 Live\",\"baseUrl\":\"" + origin + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }
}
