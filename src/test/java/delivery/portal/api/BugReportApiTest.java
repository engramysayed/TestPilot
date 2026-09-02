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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:bugreportapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-bug-report-api",
        "delivery.work-dir=./target/test-delivery-work-bug-report-api"
})
@AutoConfigureMockMvc
public class BugReportApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryPortalProperties props;

    @Test
    public void dryRunExecuteRun_bugReportJsonAndCsv_containFailRows() throws Exception {
        Assert.assertTrue(props.isDryRun(), "Test expects dry-run mode");

        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bug Report API\"}"))
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

        MvcResult reportRes = mockMvc.perform(get("/api/execute-runs/" + jobId + "/bug-report")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.rows").isArray())
                .andReturn();

        JSONObject report = new JSONObject(reportRes.getResponse().getContentAsString());
        JSONArray rows = report.getJSONArray("rows");
        Assert.assertTrue(rows.length() > 0, "expected at least one FAIL row");

        boolean sawFail = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            Assert.assertEquals(row.getString("qaStatus"), "FAIL");
            Assert.assertTrue(row.has("tcId"));
            Assert.assertTrue(row.has("title"));
            Assert.assertTrue(row.has("failureReason"));
            Assert.assertTrue(row.getString("failureReason").contains("dry-run"));
            Assert.assertTrue(row.has("screenshotUrls"));
            Assert.assertTrue(row.get("screenshotUrls") instanceof JSONArray);
            if ("TC_001".equals(row.getString("tcId"))) {
                sawFail = true;
            }
        }
        Assert.assertTrue(sawFail, "expected TC_001 in bug report rows");

        mockMvc.perform(get("/api/execute-runs/" + jobId + "/bug-report.csv")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("bug-report-" + jobId + ".csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tcId,title,qaStatus")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TC_001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FAIL")));
    }

    @Test
    public void dryRunExecuteRun_bugReport_includesDesignMismatchOnPassedTc() throws Exception {
        Assert.assertTrue(props.isDryRun(), "Test expects dry-run mode");

        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bug Report Mismatch\"}"))
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

        Path irFile = locateIrFile(jobId, "TC_002");
        if (irFile != null && Files.isRegularFile(irFile)) {
            JSONObject ir = new JSONObject(Files.readString(irFile));
            ir.put("status", "PASSED");
            ir.put("failureReason", "");
            Files.writeString(irFile, ir.toString(2));
        } else {
            irFile = locateIrFile(jobId, "TC_001");
            Assert.assertNotNull(irFile, "expected IR draft for bug report mismatch test");
            JSONObject ir = new JSONObject(Files.readString(irFile));
            ir.put("status", "PASSED");
            ir.put("failureReason", "");
            Files.writeString(irFile, ir.toString(2));
        }

        String tcId = irFile.getFileName().toString().replace(".json", "");
        Path evidenceDir = irFile.getParent().getParent()
                .resolve("evidence").resolve(tcId);
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("design-compare.json"),
                new JSONObject()
                        .put("status", "MISMATCH")
                        .put("tcId", tcId)
                        .put("reason", "test mismatch")
                        .toString(2));

        MvcResult reportRes = mockMvc.perform(get("/api/execute-runs/" + jobId + "/bug-report")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn();

        JSONObject report = new JSONObject(reportRes.getResponse().getContentAsString());
        JSONArray rows = report.getJSONArray("rows");
        boolean sawMismatchPass = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (tcId.equals(row.getString("tcId"))
                    && "PASS".equals(row.getString("qaStatus"))
                    && "MISMATCH".equals(row.optString("designCompareStatus"))) {
                sawMismatchPass = true;
            }
        }
        Assert.assertTrue(sawMismatchPass, "expected PASSED tc with design MISMATCH in bug report");
    }

    private Path locateIrFile(String jobId, String tcId) throws Exception {
        Path runsRoot = Path.of(props.getStoreRoot());
        String suffix = "/execute-runs/" + jobId + "/ir/" + tcId + ".json";
        try (var walk = Files.walk(runsRoot)) {
            return walk.filter(p -> p.toString().replace('\\', '/').endsWith(suffix))
                    .findFirst()
                    .orElse(null);
        }
    }
}
