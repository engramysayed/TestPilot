package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:triage-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-triage-api",
        "delivery.work-dir=./target/test-delivery-work-triage-api"
})
@AutoConfigureMockMvc
public class JobTriageApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-triage-api");
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
    public void userCorrectionIsPersistedSeparatelyFromSuggestion() throws Exception {
        String projectId = createProject();
        String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        String jobId = "job_triage_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        JobRecord job = new JobRecord(
                jobId, projectId, adminId(), "NEW",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://staging.example/", "", "", false);
        job.setTenantId(tenantId);
        job.setMessage("heal_exhausted no locator");
        job.setStatus(JobRecord.Status.FAILED);
        store.saveJob(job);

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureClass").value("LOCATOR"))
                .andExpect(jsonPath("$.failureClassUserCorrected").value(false));

        mockMvc.perform(post("/api/jobs/" + jobId + "/classification")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"ASSERTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureClassSuggested").value("LOCATOR"))
                .andExpect(jsonPath("$.failureClass").value("ASSERTION"))
                .andExpect(jsonPath("$.failureClassUserCorrected").value(true));

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureClassSuggested").value("LOCATOR"))
                .andExpect(jsonPath("$.failureClass").value("ASSERTION"))
                .andExpect(jsonPath("$.failureClassUserCorrected").value(true));
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Triage Project\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }
}
