package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.FailureEvidenceService;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:failure-pack;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-failure-pack",
        "delivery.work-dir=./target/test-delivery-work-failure-pack"
})
@AutoConfigureMockMvc
public class FailurePackApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @Autowired
    private PortalUserRepository users;

    @Autowired
    private FailureEvidenceService evidence;

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    @BeforeMethod
    public void clean() throws Exception {
        Path root = Path.of("./target/test-delivery-store-failure-pack");
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

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"FP\",\"baseUrl\":\"https://example.com/\"}"))
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void failurePack_failedJob_returnsZip() throws Exception {
        String projectId = createProject();
        Long uid = adminId();
        String jobId = "exec_fp01";
        JobRecord job = new JobRecord(jobId, projectId, uid, "EXECUTE",
                Path.of("noop.xlsx"), "https://example.com/", "", "", false, JobRecord.JobKind.EXECUTE);
        job.setStatus(JobRecord.Status.FAILED);
        job.setCompletedAt(Instant.now());
        store.saveJob(job);
        evidence.writeFailure(projectId, jobId, "TC_1", null, 1,
                "Assert failed", "https://example.com/login", "execute",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47}, null, null);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + jobId + "/failure-pack")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("zip")));
    }

    @Test
    public void failurePack_greenJob_404() throws Exception {
        String projectId = createProject();
        Long uid = adminId();
        String jobId = "exec_fp02";
        JobRecord job = new JobRecord(jobId, projectId, uid, "EXECUTE",
                Path.of("noop.xlsx"), "https://example.com/", "", "", false, JobRecord.JobKind.EXECUTE);
        job.setStatus(JobRecord.Status.COMPLETED);
        store.saveJob(job);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + jobId + "/failure-pack")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }
}
