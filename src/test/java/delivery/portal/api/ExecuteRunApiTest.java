package delivery.portal.api;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.PortalApplication;
import org.json.JSONArray;
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
import java.util.Base64;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:executerunapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-execute-api",
        "delivery.work-dir=./target/test-delivery-work-execute-api"
})
@AutoConfigureMockMvc
public class ExecuteRunApiTest extends AbstractTestNGSpringContextTests {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryPortalProperties props;

    @Test
    public void dryRunExecuteRun_poll_listTcs_screenshot404() throws Exception {
        Assert.assertTrue(props.isDryRun(), "Test expects dry-run mode");

        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Execute API\"}"))
                .andExpect(status().isCreated())
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

        MvcResult jobRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .file(excel)
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn();
        String jobId = new JSONObject(jobRes.getResponse().getContentAsString()).getString("jobId");
        Assert.assertTrue(jobId.startsWith("exec_"));

        String jobStatus = "QUEUED";
        for (int i = 0; i < 80 && !"COMPLETED".equals(jobStatus) && !"FAILED".equals(jobStatus); i++) {
            Thread.sleep(250);
            MvcResult poll = mockMvc.perform(get("/api/execute-runs/" + jobId)
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobKind").value("EXECUTE"))
                    .andReturn();
            jobStatus = new JSONObject(poll.getResponse().getContentAsString()).getString("status");
        }
        Assert.assertEquals(jobStatus, "COMPLETED");

        MvcResult tcsRes = mockMvc.perform(get("/api/execute-runs/" + jobId + "/tcs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray tcs = new JSONArray(tcsRes.getResponse().getContentAsString());
        Assert.assertTrue(tcs.length() > 0);
        for (int i = 0; i < tcs.length(); i++) {
            JSONObject tc = tcs.getJSONObject(i);
            Assert.assertEquals(tc.getString("qaStatus"), "FAIL");
        }

        mockMvc.perform(get("/api/execute-runs/" + jobId + "/tcs/TC_001/screenshots/missing.png")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isNotFound());
    }

    @Test
    public void dryRunExecute_withDesignReference_writesSkippedCompareEvidence() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Execute Design Compare\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());

        MockMultipartFile ref = new MockMultipartFile(
                "file", "ref.png", "image/png", TINY_PNG);
        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(ref)
                        .param("tcId", "TC_001")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated());

        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        MvcResult jobRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .file(excel)
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(jobRes.getResponse().getContentAsString()).getString("jobId");

        String jobStatus = "QUEUED";
        for (int i = 0; i < 80 && !"COMPLETED".equals(jobStatus) && !"FAILED".equals(jobStatus); i++) {
            Thread.sleep(250);
            MvcResult poll = mockMvc.perform(get("/api/execute-runs/" + jobId)
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                    .andExpect(status().isOk())
                    .andReturn();
            jobStatus = new JSONObject(poll.getResponse().getContentAsString()).getString("status");
        }
        Assert.assertEquals(jobStatus, "COMPLETED");

        Path compareJson = Path.of(props.getStoreRoot())
                .resolve("example-com")
                .resolve(projectId)
                .resolve("execute-runs")
                .resolve(jobId)
                .resolve("evidence")
                .resolve("TC_001")
                .resolve("design-compare.json");
        if (!Files.isRegularFile(compareJson)) {
            // Domain folder may vary; locate by walking execute-runs
            Path runsRoot = Path.of(props.getStoreRoot());
            try (var walk = Files.walk(runsRoot)) {
                compareJson = walk.filter(p -> p.toString().replace('\\', '/').endsWith(
                                "/execute-runs/" + jobId + "/evidence/TC_001/design-compare.json"))
                        .findFirst()
                        .orElse(compareJson);
            }
        }
        Assert.assertTrue(Files.isRegularFile(compareJson), "expected design-compare.json at " + compareJson);
        JSONObject evidence = new JSONObject(Files.readString(compareJson));
        Assert.assertEquals(evidence.getString("status"), "SKIPPED");
        Assert.assertEquals(evidence.getString("tcId"), "TC_001");
        Assert.assertTrue(evidence.getString("reason").toLowerCase().contains("dry-run"));
    }
}
