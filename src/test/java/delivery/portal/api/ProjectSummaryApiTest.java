package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:projectsummary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-summary",
        "delivery.work-dir=./target/test-delivery-work-summary"
})
@AutoConfigureMockMvc
public class ProjectSummaryApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-summary");
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    @Test
    public void summary_emptyProjectStripAndNoJobs() throws Exception {
        String id = createProject();
        mockMvc.perform(get("/api/projects/" + id + "/summary")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strip.libraryCaseCount").value(0))
                .andExpect(jsonPath("$.strip.latestPackage").value("none"))
                .andExpect(jsonPath("$.strip.prove.total").value(0))
                .andExpect(jsonPath("$.strip.jobRunning").value(false))
                .andExpect(jsonPath("$.recentJobs").isEmpty());
    }

    @Test
    public void summary_includesRecentJobsAndLatestPackage() throws Exception {
        String id = createProject();
        Path root = store.projectDiskRoot(id);
        Files.createDirectories(root.resolve("versions"));
        try (var zos = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(root.resolve("versions/v2.zip")))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("README.md"));
            zos.write("x".getBytes());
            zos.closeEntry();
        }
        Long ownerId = users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
        JobEntity job = new JobEntity();
        job.setJobId("job_summary_test_1");
        job.setProjectId(id);
        job.setOwnerUserId(ownerId);
        job.setJobKind("CONVERT");
        job.setStatus("COMPLETED");
        job.setMode("NEW");
        job.setPassedCount(2);
        job.setTodoCount(1);
        job.setMessage("done");
        job.setCreatedAt(Instant.parse("2026-09-09T12:00:00Z"));
        jobRepository.save(job);

        mockMvc.perform(get("/api/projects/" + id + "/summary")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strip.latestPackage").value("v2"))
                .andExpect(jsonPath("$.recentJobs[0].jobId").value("job_summary_test_1"))
                .andExpect(jsonPath("$.recentJobs[0].kindLabel").value("Automate"))
                .andExpect(jsonPath("$.recentJobs[0].passedCount").value(2));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Summary Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
