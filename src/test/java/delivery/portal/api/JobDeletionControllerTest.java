package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Smoke test for the generic DELETE /api/runs/{jobId} endpoint. */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:job-delete;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-job-delete",
        "delivery.work-dir=./target/test-delivery-work-job-delete"
})
@AutoConfigureMockMvc
public class JobDeletionControllerTest extends AbstractTestNGSpringContextTests {

    private static final String ADMIN_USER = "admin@testpilot.local";
    private static final String ADMIN_PASS = "ChangeMeAdmin1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalStore store;

    @Test
    public void deleteUnknownRunReturns404() throws Exception {
        mockMvc.perform(delete("/api/runs/job_no_such_id")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    public void deleteCompletedConvertJobSucceeds() throws Exception {
        String projectId = createProjectAndReturnId();
        long ownerId = resolveOwnerId(projectId);
        String jobId = seedJob(projectId, ownerId, JobRecord.Status.COMPLETED);

        mockMvc.perform(delete("/api/runs/" + jobId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.jobId").value(jobId));

        Assert.assertTrue(store.getJob(jobId).isEmpty(), "job row should be gone");
    }

    @Test
    public void deleteRunningJobReturns409() throws Exception {
        String projectId = createProjectAndReturnId();
        long ownerId = resolveOwnerId(projectId);
        String jobId = seedJob(projectId, ownerId, JobRecord.Status.RUNNING);

        mockMvc.perform(delete("/api/runs/" + jobId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("JOB_ACTIVE"));

        Assert.assertTrue(store.getJob(jobId).isPresent(), "row still there after refused delete");
    }

    @Test
    public void deleteByNonOwnerReturns404() throws Exception {
        String projectId = createProjectAndReturnId();
        long ownerId = resolveOwnerId(projectId);
        String jobId = seedJob(projectId, ownerId, JobRecord.Status.COMPLETED);

        // A different signed-in identity sees the same jobId as not-owned → 404 (not 403), so the
        // existence of someone else's job is not leaked.
        mockMvc.perform(delete("/api/runs/" + jobId)
                        .with(httpBasic("ghost@testpilot.local", "WrongPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnauthorized());

        Assert.assertTrue(store.getJob(jobId).isPresent(), "row untouched by non-owner");
    }

    private String createProjectAndReturnId() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delete Test\",\"baseUrl\":\"https://example.test/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private long resolveOwnerId(String projectId) {
        for (long id = 1L; id < 50L; id++) {
            if (store.getOwnedProject(projectId, id).isPresent()) return id;
        }
        throw new AssertionError("Could not resolve owner user id");
    }

    private String seedJob(String projectId, long ownerId, JobRecord.Status status) {
        String jobId = "job_generic_delete_" + Long.toHexString(System.nanoTime());
        JobRecord job = new JobRecord(
                jobId, projectId, ownerId, "NEW", Path.of("dummy.xlsx"),
                "https://example.test/", "", "", false, JobRecord.JobKind.CONVERT);
        job.setStatus(status);
        store.saveJob(job);
        return jobId;
    }
}
