package delivery.portal.api;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.PortalApplication;
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
import java.util.zip.ZipFile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-api",
        "delivery.work-dir=./target/test-delivery-work-api"
})
@AutoConfigureMockMvc
public class PortalApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryPortalProperties props;

    @Autowired
    private PortalStore store;

    @Test
    public void unauthenticated_api_isRejected() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void createProject_upload_poll_download() throws Exception {
        Assert.assertTrue(props.isDryRun(), "Test expects dry-run mode");

        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Portal API\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").exists())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());

        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        MvcResult jobRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excel)
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn();
        String jobId = new JSONObject(jobRes.getResponse().getContentAsString()).getString("jobId");

        String jobStatus = "QUEUED";
        for (int i = 0; i < 80 && !"COMPLETED".equals(jobStatus) && !"FAILED".equals(jobStatus); i++) {
            Thread.sleep(250);
            MvcResult poll = mockMvc.perform(get("/api/jobs/" + jobId)
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                    .andExpect(status().isOk())
                    .andReturn();
            jobStatus = new JSONObject(poll.getResponse().getContentAsString()).getString("status");
        }
        Assert.assertEquals(jobStatus, "COMPLETED");

        mockMvc.perform(get("/api/jobs/" + jobId + "/download")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk());

        Path zipPath = store.getJob(jobId).orElseThrow().getZipPath();
        Assert.assertTrue(Files.isRegularFile(zipPath));
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            Assert.assertNotNull(zf.getEntry("src/main/java/project/drivers/WebDriverFactory.java"));
            Assert.assertNotNull(zf.getEntry(".github/workflows/E2E.yml"));
        }
    }

    @Test
    public void invalidExcel_returns400() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Excel\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());

        MockMultipartFile excel = new MockMultipartFile("excel", "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-an-excel".getBytes());

        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excel)
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_EXCEL"));
    }

    @Test
    public void jobWithoutBaseUrl_returns400() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Base URL\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excel)
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_BASE_URL"));
    }

    @Test
    public void accountTargets_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/account/targets")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets").isArray())
                .andExpect(jsonPath("$.targets").isEmpty());
    }
}
