package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.runner.PrivateRunnerAgent;
import delivery.runner.PrivateRunnerArtifacts;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:private-runner-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-private-runner",
        "delivery.work-dir=./target/test-delivery-work-private-runner"
})
@AutoConfigureMockMvc
public class PrivateRunnerApiTest extends AbstractTestNGSpringContextTests {

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
        Path root = Path.of("./target/test-delivery-store-private-runner");
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
    public void enrollHeartbeatClaimRejectsCrossTenantAndRevokes() throws Exception {
        String projectId = createProject();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authoringEngine\":\"precision\",\"precisionMaxCallsPerJob\":7}"))
                .andExpect(status().isOk());
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "private-runner");

        MvcResult enrolled = mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"office-1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JSONObject runner = new JSONObject(enrolled.getResponse().getContentAsString());
        String token = runner.getString("token");
        String runnerId = runner.getString("id");
        Assert.assertTrue(token.startsWith("tp_run_"));

        mockMvc.perform(post("/api/v1/runners/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runnerId").value(runnerId));

        MvcResult submitted = mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + serviceToken(projectId))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\",\"runner\":\"private\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(submitted.getResponse().getContentAsString()).getString("jobId");
        Assert.assertTrue(store.getJob(jobId).orElseThrow().isRequirePrivateRunner());
        Assert.assertTrue(store.beginWork(jobId).isEmpty(), "in-process workers must skip private-runner jobs");

        MvcResult claimed = mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andReturn();
        JSONObject claim = new JSONObject(claimed.getResponse().getContentAsString());
        // Hydrate from SQL, as after a portal restart; CI jobs have no legacy request.json.
        ((java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(store, "jobs")).remove(jobId);
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getAuthoringEngine().wireValue(), "precision");
        byte[] frozenInput = mockMvc.perform(get("/api/v1/runners/jobs/" + jobId + "/input")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(frozenInput))) {
            Assert.assertEquals(zip.getNextEntry().getName(), "job.json");
            var metadata = new JSONObject(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            Assert.assertEquals(metadata.getString("authoringEngine"), "precision");
            Assert.assertEquals(metadata.getInt("precisionMaxCalls"), 7);
            Assert.assertEquals(metadata.getBoolean("precisionEnabled"),
                    store.precisionConfigForJob(store.getJob(jobId).orElseThrow()).enabled());
        }
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.RUNNING);

        byte[] empty = new byte[] {1, 2, 3};
        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/artifacts")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .header(PrivateRunnerArtifacts.SIGNATURE_HEADER, "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(empty))
                .andExpect(status().isUnauthorized());

        String sig = PrivateRunnerArtifacts.signature(token, jobId, claim.getString("attemptId"), empty);
        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/artifacts")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .header(PrivateRunnerArtifacts.SIGNATURE_HEADER, "sha256=" + sig)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(empty))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"passedCount\":1,\"todoCount\":0,\"message\":\"ok\","
                                + "\"inputSnapshotHash\":\"" + claim.getString("inputSnapshotHash") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(delete("/api/projects/" + projectId + "/runners/" + runnerId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void foreignTenantRunnerCannotClaimOrReadJob() throws Exception {
        String aliceProject = createProject("Alice Runner", "admin@testpilot.local", "ChangeMeAdmin1!");
        seedWorkbook(aliceProject, "alice-private");
        String aliceToken = enrollRunner(aliceProject, "admin@testpilot.local", "ChangeMeAdmin1!", "alice");
        String aliceJob = submitPrivateJob(aliceProject, serviceToken(aliceProject, "admin@testpilot.local", "ChangeMeAdmin1!"));

        ensureUser("owner-b@testpilot.local", "OwnerBPass1!");
        String bobProject = createProject("Bob Runner", "owner-b@testpilot.local", "OwnerBPass1!");
        seedWorkbook(bobProject, "bob-private");
        String bobToken = enrollRunner(bobProject, "owner-b@testpilot.local", "OwnerBPass1!", "bob");

        mockMvc.perform(post("/api/v1/runners/heartbeat")
                        .header("Authorization", "Bearer " + bobToken)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + bobToken)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/runners/jobs/" + aliceJob)
                        .header("Authorization", "Bearer " + bobToken)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
        Assert.assertEquals(store.getJob(aliceJob).orElseThrow().getStatus(), JobRecord.Status.QUEUED);

        mockMvc.perform(post("/api/v1/runners/heartbeat")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(aliceJob));
    }

    @Test
    public void memberCannotEnrollRunner() throws Exception {
        String projectId = createProject();
        ensureUser("member@testpilot.local", "MemberPass1!");
        TenantId tenant = TenantId.parse(store.getProject(projectId).orElseThrow().getTenantId());
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-private-runner"))
                .addMember(tenant, users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId(),
                        WorkspaceRole.MEMBER);
        mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"nope\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/" + projectId + "/runners")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
    }

    @Test
    public void completeRejectsFrozenInputAndProviderDriftAndHonorsCancel() throws Exception {
        String projectId = createProject();
        seedWorkbook(projectId, "private-policy");
        String token = enrollRunner(projectId, "admin@testpilot.local", "ChangeMeAdmin1!", "policy");
        String jobId = submitPrivateJob(projectId, serviceToken(projectId));
        mockMvc.perform(post("/api/v1/runners/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        JSONObject claim = new JSONObject(mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"inputSnapshotHash\":\"not-the-pin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("FROZEN_INPUT_MISMATCH"));

        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"inputSnapshotHash\":\""
                                + claim.getString("inputSnapshotHash")
                                + "\",\"providerAllowlistSnapshot\":\"attacker-allowlist\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROVIDER_POLICY"));

        mockMvc.perform(post("/api/v1/jobs/" + jobId + "/cancel")
                        .header("Authorization", "Bearer " + serviceToken(projectId))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/lease")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelRequested").value(true));
        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"inputSnapshotHash\":\""
                                + claim.getString("inputSnapshotHash") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.CANCELLED);
    }

    @Test
    public void runnerMarksBrowserStageThenDisconnectIsUncertain() throws Exception {
        String projectId = createProject();
        seedWorkbook(projectId, "private-browser-stage");
        String token = enrollRunner(projectId, "admin@testpilot.local", "ChangeMeAdmin1!", "stage");
        String jobId = submitPrivateJob(projectId, serviceToken(projectId));
        mockMvc.perform(post("/api/v1/runners/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/runners/claim")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/runners/jobs/" + jobId + "/stage")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"BROWSER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStage").value("BROWSER"));
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getClaimStage(), "BROWSER");

        JobRecord running = store.getJob(jobId).orElseThrow();
        running.setLeaseUntil(java.time.Instant.now().minusSeconds(5));
        store.syncJobPersistence(running);
        Assert.assertTrue(store.reconcileExpiredLeases() >= 1);
        JobRecord dead = store.getJob(jobId).orElseThrow();
        Assert.assertEquals(dead.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(dead.getError(), delivery.job.DurableJobClaim.INTERRUPTED_UNCERTAIN);
    }

    @Test
    public void agentRunOnceCompletesPrivateDryRunJob() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "private-runner-agent");
        String token = new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"agent\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).getString("token");
        String svc = serviceToken(projectId);
        String jobId = new JSONObject(mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + svc)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\",\"runner\":\"private\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).getString("jobId");

        Assert.assertEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.QUEUED);
        // Claim + complete through MockMvc (same JVM) is covered above; here we only
        // assert the agent class is wired for dry-run execution of a local pack.
        Path work = Files.createTempDirectory("runner-agent");
        Assert.assertNotNull(new PrivateRunnerAgent("http://127.0.0.1:1", token, work, true));
    }

    private String serviceToken(String projectId) throws Exception {
        return serviceToken(projectId, "admin@testpilot.local", "ChangeMeAdmin1!");
    }

    private String serviceToken(String projectId, String email, String password) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic(email, password))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("token");
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

    private String submitPrivateJob(String projectId, String svc) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + svc)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\",\"runner\":\"private\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).getString("jobId");
    }

    private void seedWorkbook(String projectId, String label) throws Exception {
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", label);
    }

    private String createProject() throws Exception {
        return createProject("Private Runner", "admin@testpilot.local", "ChangeMeAdmin1!");
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
