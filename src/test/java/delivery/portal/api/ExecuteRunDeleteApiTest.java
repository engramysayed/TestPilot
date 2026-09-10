package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:exec-delete;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-exec-delete",
        "delivery.work-dir=./target/test-delivery-work-exec-delete"
})
@AutoConfigureMockMvc
public class ExecuteRunDeleteApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @Test
    public void deleteExecuteRun_removesJobAndDiskFolder() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Exec Delete\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.test/\"}"))
                .andExpect(status().isOk());

        long ownerId = -1L;
        for (long id = 1L; id < 50L; id++) {
            if (store.getOwnedProject(projectId, id).isPresent()) {
                ownerId = id;
                break;
            }
        }
        Assert.assertTrue(ownerId > 0, "Could not resolve owner user id");

        String jobId = "exec_delete_ut_01";
        Path runDir = store.filesystemStoreFor(projectId).projectRoot(projectId)
                .resolve("execute-runs").resolve(jobId);
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("marker.txt"), "x");

        JobRecord job = new JobRecord(
                jobId, projectId, ownerId, "EXECUTE", Path.of("dummy.xlsx"),
                "https://example.test/", "", "", false, JobRecord.JobKind.EXECUTE);
        job.setStatus(JobRecord.Status.COMPLETED);
        store.saveJob(job);

        mockMvc.perform(delete("/api/execute-runs/" + jobId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        Assert.assertTrue(store.getJob(jobId).isEmpty());
        Assert.assertFalse(Files.exists(runDir));
    }
}
