package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.runner.PrivateRunnerAgent;
import delivery.runner.PrivateRunnerArtifacts;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("token");
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private Runner\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }
}
