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
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delete Test\",\"baseUrl\":\"https://example.test/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        long ownerId = -1L;
        for (long id = 1L; id < 50L; id++) {
            if (store.getOwnedProject(projectId, id).isPresent()) {
                ownerId = id;
                break;
            }
        }
        Assert.assertTrue(ownerId > 0, "Could not resolve owner user id");

        String jobId = "job_generic_delete_ut";
        JobRecord job = new JobRecord(
                jobId, projectId, ownerId, "NEW", Path.of("dummy.xlsx"),
                "https://example.test/", "", "", false, JobRecord.JobKind.CONVERT);
        job.setStatus(JobRecord.Status.COMPLETED);
        store.saveJob(job);

        mockMvc.perform(delete("/api/runs/" + jobId)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.jobId").value(jobId));

        Assert.assertTrue(store.getJob(jobId).isEmpty(), "job row should be gone");
    }
}
