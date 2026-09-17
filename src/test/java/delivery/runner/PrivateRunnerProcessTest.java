package delivery.runner;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:private-runner-proc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-private-runner-proc",
        "delivery.work-dir=./target/test-delivery-work-private-runner-proc"
})
@AutoConfigureMockMvc
public class PrivateRunnerProcessTest extends AbstractTestNGSpringContextTests {

    @LocalServerPort
    private int port;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GeneratedWorkbookService workbooks;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-private-runner-proc");
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

    @Test
    public void separateAgentProcessCompletesPrivateJobAndRejectsForeignToken() throws Exception {
        String projectId = createProject("Runner Proc", "admin@testpilot.local", "ChangeMeAdmin1!");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "private-proc");
        String token = enrollRunner(projectId, "admin@testpilot.local", "ChangeMeAdmin1!", "proc");
        String svc = serviceToken(projectId, "admin@testpilot.local", "ChangeMeAdmin1!");
        String jobId = submitPrivate(projectId, svc);

        ensureUser("owner-b@testpilot.local", "OwnerBPass1!");
        String foreignProject = createProject("Foreign Proc", "owner-b@testpilot.local", "OwnerBPass1!");
        workbooks.saveFromCases(foreignProject, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "private-foreign");
        String foreignToken = enrollRunner(foreignProject, "owner-b@testpilot.local", "OwnerBPass1!", "foreign");

        Path work = Files.createTempDirectory("runner-proc");
        Process bad = startAgent(foreignToken, work);
        try {
            Thread.sleep(4000);
            Assert.assertEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.QUEUED);
        } finally {
            bad.destroy();
            bad.waitFor(5, TimeUnit.SECONDS);
        }

        Process agent = startAgent(token, work);
        try {
            JobRecord.Status status = waitStatus(jobId, JobRecord.Status.COMPLETED, 45);
            Assert.assertEquals(status, JobRecord.Status.COMPLETED);
        } finally {
            agent.destroy();
            agent.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void disconnectRequeuesAdmittedPrivateJob() throws Exception {
        String projectId = createProject("Runner Disc", "admin@testpilot.local", "ChangeMeAdmin1!");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "private-disc");
        String token = new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"disc\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).getString("token");
        mockMvc.perform(post("/api/v1/runners/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        String svc = new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).getString("token");
        String jobId = new JSONObject(mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + svc)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\",\"runner\":\"private\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).getString("jobId");
        mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        JobRecord running = store.getJob(jobId).orElseThrow();
        running.setLeaseUntil(java.time.Instant.now().minusSeconds(5));
        store.syncJobPersistence(running);
        Assert.assertTrue(store.reconcileExpiredLeases() >= 1);
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.QUEUED);
    }

    private Process startAgent(String token, Path work) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                "delivery.runner.PrivateRunnerAgent",
                "--portal", "http://127.0.0.1:" + port,
                "--token", token,
                "--work-dir", work.toString(),
                "--dry-run", "true"
        );
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private String enrollRunner(String projectId, String email, String password, String label) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic(email, password))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).getString("token");
    }

    private String serviceToken(String projectId, String email, String password) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic(email, password))
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

    private String createProject(String name, String email, String password) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(email, password))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private void ensureUser(String email, String password) {
        if (!users.existsByEmailIgnoreCase(email)) {
            PortalUser u = new PortalUser();
            u.setEmail(email);
            u.setPasswordHash(encoder.encode(password));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }
}
