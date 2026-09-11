package delivery.portal.api;

import delivery.job.AuthoringJobRequestFiles;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
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
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:authoringengineapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-authoring-api",
        "delivery.work-dir=./target/test-delivery-work-authoring-api"
})
@AutoConfigureMockMvc
public class AuthoringEngineApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @Test
    public void automateJobWritesPrecisionToRequestJson() throws Exception {
        String projectId = createProjectWithBaseUrl();
        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        MvcResult jobRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excel)
                        .param("mode", "NEW")
                        .param("authoringEngine", "precision")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(jobRes.getResponse().getContentAsString()).getString("jobId");

        Path requestPath = AuthoringJobRequestFiles.requestPath(
                store.projectDiskRoot(projectId), JobRecord.JobKind.CONVERT, jobId);
        Assert.assertTrue(Files.isRegularFile(requestPath), "request.json should exist");
        JSONObject body = new JSONObject(Files.readString(requestPath));
        Assert.assertEquals("precision", body.getString("authoringEngine"));
    }

    @Test
    public void executeRunWritesPrecisionToRequestJson() throws Exception {
        String projectId = createProjectWithBaseUrl();
        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        MvcResult jobRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .file(excel)
                        .param("authoringEngine", "precision")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(jobRes.getResponse().getContentAsString()).getString("jobId");

        Path requestPath = AuthoringJobRequestFiles.requestPath(
                store.projectDiskRoot(projectId), JobRecord.JobKind.EXECUTE, jobId);
        Assert.assertTrue(Files.isRegularFile(requestPath), "request.json should exist");
        JSONObject body = new JSONObject(Files.readString(requestPath));
        Assert.assertEquals("precision", body.getString("authoringEngine"));
    }

    private String createProjectWithBaseUrl() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Authoring API\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());
        return projectId;
    }
}
