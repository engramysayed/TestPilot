package delivery.portal.api;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:compare-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-compare-api",
        "delivery.work-dir=./target/test-delivery-work-compare-api"
})
@AutoConfigureMockMvc
public class JobCompareApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-compare-api");
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
    public void compareUsesRecordedDraftsAndLeavesOriginalEvidence() throws Exception {
        String projectId = createProject();
        String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        JobRecord first = executeJob(projectId, tenantId, "job_cmp_a", JobRecord.Status.FAILED, 0, 1);
        JobRecord second = executeJob(projectId, tenantId, "job_cmp_b", JobRecord.Status.COMPLETED, 1, 0);
        second.setParentJobId(first.getJobId());
        store.saveJob(second);
        writeDraft(first, TcDraftStatus.TODO, "Welcome", "https://example/error");
        writeDraft(second, TcDraftStatus.PASSED, "Welcome", "https://example/home");

        mockMvc.perform(get("/api/jobs/" + first.getJobId() + "/compare")
                        .param("other", second.getJobId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinsMatch").value(true))
                .andExpect(jsonPath("$.leftPreserved").value(true))
                .andExpect(jsonPath("$.divergence.field").value("observed"));

        mockMvc.perform(get("/api/jobs/" + first.getJobId() + "/attempts")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootJobId").value(first.getJobId()))
                .andExpect(jsonPath("$.attempts.length()").value(2));

        mockMvc.perform(get("/api/jobs/" + first.getJobId() + "/intermittency")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleSize").value(2))
                .andExpect(jsonPath("$.minSample").value(3))
                .andExpect(jsonPath("$.verdict").value("INSUFFICIENT_SAMPLE"));

        Assert.assertEquals(store.getJob(first.getJobId()).orElseThrow().getStatus(),
                JobRecord.Status.FAILED);
        Assert.assertEquals(store.getJob(second.getJobId()).orElseThrow().getParentJobId(),
                first.getJobId());
    }

    @Test
    public void rerunMintsANewAttemptWithPinnedInputs() throws Exception {
        String projectId = createProject();
        String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        JobRecord source = executeJob(projectId, tenantId, "job_cmp_src", JobRecord.Status.FAILED, 0, 1);
        MvcResult rerun = mockMvc.perform(post("/api/jobs/" + source.getJobId() + "/rerun")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.parentJobId").value(source.getJobId()))
                .andReturn();
        String newId = new JSONObject(rerun.getResponse().getContentAsString()).getString("jobId");
        Assert.assertNotEquals(newId, source.getJobId());
        Assert.assertEquals(store.getJob(source.getJobId()).orElseThrow().getStatus(),
                JobRecord.Status.FAILED);
        JobRecord copy = store.getJob(newId).orElseThrow();
        Assert.assertEquals(copy.getLibraryRevisionId(), source.getLibraryRevisionId());
        Assert.assertEquals(copy.getEnvironmentRevisionId(), source.getEnvironmentRevisionId());
    }

    @Test
    public void rerunRematerializesPinnedLibraryAfterTempExcelIsDeleted() throws Exception {
        String projectId = createProject();
        byte[] xlsx = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        mockMvc.perform(multipart("/api/projects/" + projectId + "/generated-workbook/upload")
                        .file(new MockMultipartFile("file", "sample-manual-tcs.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx))
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        MvcResult started = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .param("useGenerated", "true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andReturn();
        String sourceId = new JSONObject(started.getResponse().getContentAsString()).getString("jobId");
        JobRecord source = waitTerminal(sourceId);
        Assert.assertEquals(source.getStatus(), JobRecord.Status.COMPLETED);
        int passed = source.getPassedCount();
        int todo = source.getTodoCount();
        Path excel = source.getExcelPath();
        if (excel != null) {
            Files.deleteIfExists(excel);
        }

        MvcResult rerun = mockMvc.perform(post("/api/jobs/" + sourceId + "/rerun")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andReturn();
        String newId = new JSONObject(rerun.getResponse().getContentAsString()).getString("jobId");
        JobRecord copy = waitTerminal(newId);
        Assert.assertEquals(copy.getStatus(), JobRecord.Status.COMPLETED, copy.getMessage());
        Assert.assertEquals(copy.getParentJobId(), sourceId);
        Assert.assertEquals(copy.getLibraryRevisionId(), source.getLibraryRevisionId());
        Assert.assertEquals(copy.getPassedCount(), passed);
        Assert.assertEquals(copy.getTodoCount(), todo);
        JobRecord original = store.getJob(sourceId).orElseThrow();
        Assert.assertEquals(original.getStatus(), JobRecord.Status.COMPLETED);
        Assert.assertEquals(original.getPassedCount(), passed);
        Assert.assertEquals(original.getTodoCount(), todo);
    }

    private JobRecord waitTerminal(String jobId) throws Exception {
        for (int i = 0; i < 40; i++) {
            JobRecord job = store.getJob(jobId).orElseThrow();
            if (JobRecord.isTerminal(job.getStatus())) {
                return job;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("job did not finish: " + jobId);
    }

    @Test
    public void populatedHistoryShowsFallbackCompareClassifyAndPreservesOriginalAfterRerun() throws Exception {
        String projectId = createProject();
        String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        JobRecord first = executeJob(projectId, tenantId, "job_hist_a", JobRecord.Status.FAILED, 0, 1);
        first.setProvidersUsed("precision,keel");
        first.setFallbackUsed(true);
        first.setFallbackReason("PRECISION_FALLBACK");
        first.setMessage("heal_exhausted no locator");
        store.saveJob(first);
        JobRecord second = executeJob(projectId, tenantId, "job_hist_b", JobRecord.Status.COMPLETED, 1, 0);
        second.setParentJobId(first.getJobId());
        second.setProvidersUsed("keel");
        store.saveJob(second);
        writeDraft(first, TcDraftStatus.TODO, "Welcome", "https://example/error");
        writeDraft(second, TcDraftStatus.PASSED, "Welcome", "https://example/home");

        mockMvc.perform(get("/api/jobs/" + first.getJobId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providersUsed").value("precision,keel"))
                .andExpect(jsonPath("$.fallbackUsed").value(true))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.passedCount").value(0));

        mockMvc.perform(post("/api/jobs/" + first.getJobId() + "/classification")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"ASSERTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureClass").value("ASSERTION"))
                .andExpect(jsonPath("$.failureClassUserCorrected").value(true));

        mockMvc.perform(get("/api/jobs/" + first.getJobId() + "/compare")
                        .param("other", second.getJobId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinsMatch").value(true))
                .andExpect(jsonPath("$.leftPreserved").value(true));

        MvcResult rerun = mockMvc.perform(post("/api/jobs/" + first.getJobId() + "/rerun")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andReturn();
        String newId = new JSONObject(rerun.getResponse().getContentAsString()).getString("jobId");

        JobRecord original = store.getJob(first.getJobId()).orElseThrow();
        Assert.assertEquals(original.getStatus(), JobRecord.Status.FAILED);
        Assert.assertEquals(original.getPassedCount(), 0);
        Assert.assertEquals(original.getLibraryRevisionId(), "rev_cmp");
        Assert.assertNotEquals(newId, first.getJobId());
        JobRecord attempt = store.getJob(newId).orElseThrow();
        Assert.assertEquals(attempt.getParentJobId(), first.getJobId());
        Assert.assertEquals(attempt.getLibraryRevisionId(), original.getLibraryRevisionId());
        Assert.assertEquals(attempt.getEnvironmentRevisionId(), original.getEnvironmentRevisionId());
        Assert.assertEquals(attempt.getProviderAllowlistSnapshot(), original.getProviderAllowlistSnapshot());

        mockMvc.perform(get("/api/jobs/" + first.getJobId() + "/attempts")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(3));

        mockMvc.perform(get("/api/jobs/" + first.getJobId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureClass").value("ASSERTION"))
                .andExpect(jsonPath("$.failureClassUserCorrected").value(true))
                .andExpect(jsonPath("$.providersUsed").value("precision,keel"));
    }

    private JobRecord executeJob(
            String projectId, String tenantId, String jobId,
            JobRecord.Status status, int passed, int todo) {
        JobRecord job = new JobRecord(
                jobId, projectId, adminId(), "NEW",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://staging.example/", "", "", false, JobRecord.JobKind.EXECUTE);
        job.setTenantId(tenantId);
        job.setLibraryRevisionId("rev_cmp");
        job.setEnvironmentRevisionId("envrev_cmp");
        job.setProviderAllowlistSnapshot("ollama");
        job.setStatus(status);
        job.setPassedCount(passed);
        job.setTodoCount(todo);
        store.saveJob(job);
        return job;
    }

    private void writeDraft(JobRecord job, TcDraftStatus status, String expected, String url)
            throws Exception {
        Path runRoot = store.projectDiskRoot(job.getProjectId())
                .resolve("execute-runs").resolve(job.getJobId());
        ProvenStep step = new ProvenStep(
                "TC1", "Home", "elementAction", "click",
                "id", "go", "", "text", expected, status == TcDraftStatus.PASSED, "ok");
        TcDraft draft = new TcDraft(
                "TC1", "Home", "1. Click", expected, status, java.util.List.of(step),
                java.util.List.of(), false, -1, "",
                status == TcDraftStatus.PASSED ? "" : "assert failed",
                "", 0, url);
        new TcDraftStore(runRoot).write(draft);
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Compare Project\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }
}
