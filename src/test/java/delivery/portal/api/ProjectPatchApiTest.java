package delivery.portal.api;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.PortalApplication;
import delivery.portal.service.PortalStore;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.PortalUserRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:projectpatch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-patch",
        "delivery.work-dir=./target/test-delivery-work-patch"
})
@AutoConfigureMockMvc
public class ProjectPatchApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    @Autowired
    private DeliveryPortalProperties props;

    @Autowired
    private PortalStore portalStore;

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Patch Me\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void patch_renameAndBaseUrl() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"baseUrl\":\"https://app.example.com/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.baseUrl").value("https://app.example.com/"));
    }

    @Test
    public void patch_savesPreferredHooksOnTheDomainFolder() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://opssit.axispay.app/login\",\"preferredHooks\":\"data-axis-test-id\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredHooks").value("data-axis-test-id"));
        Path file = Path.of(props.getStoreRoot(), "opssit-axispay-app", "preferred-hooks.json");
        org.testng.Assert.assertTrue(Files.isRegularFile(file), file.toString());
        mockMvc.perform(get("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredHooks").value("data-axis-test-id"));
    }

    @Test
    public void archive_hidesFromDefaultList() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId=='" + id + "')]").doesNotExist());

        mockMvc.perform(get("/api/projects?includeArchived=true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId=='" + id + "')].archived").value(true));
    }

    @Test
    public void archiveWhileJobRunning_returns409() throws Exception {
        String id = createProject();
        Long ownerId = users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
        JobEntity job = new JobEntity();
        job.setJobId("job_test_queued");
        job.setProjectId(id);
        job.setOwnerUserId(ownerId);
        job.setMode("NEW");
        job.setStatus("QUEUED");
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("JOB_RUNNING"));
    }

    @Test
    public void jobOnArchivedProject_returns409() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\",\"archived\":true}"))
                .andExpect(status().isOk());

        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/projects/" + id + "/jobs")
                        .file(excel)
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));
    }

    @Test
    public void screenshotRoute_returnsPngWhenFileExists() throws Exception {
        String id = createProject();
        mockMvc.perform(patch("/api/projects/" + id)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());
        Path evidenceDir = portalStore.projectDiskRoot(id).resolve("evidence").resolve("tc1");
        Files.createDirectories(evidenceDir);
        Path png = evidenceDir.resolve("step-001.png");
        Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00});

        mockMvc.perform(get("/api/projects/" + id + "/tcs/tc1/screenshots/step-001.png")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("image/png")));
    }

    @Test
    public void listProjects_includesLastModified() throws Exception {
        String id = createProject();
        Long ownerId = users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
        JobEntity job = new JobEntity();
        job.setJobId("job_test_completed");
        job.setProjectId(id);
        job.setOwnerUserId(ownerId);
        job.setMode("NEW");
        job.setStatus("COMPLETED");
        job.setCreatedAt(Instant.now());
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        mockMvc.perform(get("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId=='" + id + "')].lastModifiedLabel").exists())
                .andExpect(jsonPath("$[?(@.projectId=='" + id + "')].lastModified").exists());
    }
}
