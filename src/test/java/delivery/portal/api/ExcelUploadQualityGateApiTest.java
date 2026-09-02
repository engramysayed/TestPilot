package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.excel.ManualTcExcelWriter;
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
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:exceluploadgate;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-exceluploadgate",
        "delivery.work-dir=./target/test-delivery-work-exceluploadgate"
})
@AutoConfigureMockMvc
public class ExcelUploadQualityGateApiTest extends AbstractTestNGSpringContextTests {

    private static final String BASE_URL = "https://www.facebook.com/";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void automateJob_badPhoneFieldExcel_returnsQualityGateBeforeEnqueue() throws Exception {
        String projectId = createProjectWithBaseUrl();
        byte[] excel = writeExcel(List.of(badPhoneFieldCase()));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excelFile(excel))
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUALITY_GATE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsStringIgnoringCase("email or phone")));

        assertNoConvertJobs(projectId);
    }

    @Test
    public void executeRun_badPhoneFieldExcel_returnsQualityGateBeforeEnqueue() throws Exception {
        String projectId = createProjectWithBaseUrl();
        byte[] excel = writeExcel(List.of(badPhoneFieldCase()));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .file(excelFile(excel))
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUALITY_GATE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsStringIgnoringCase("email or phone")));

        assertNoExecuteJobs(projectId);
    }

    @Test
    public void automateJob_blankKeelPath_passesGateAndEnqueues() throws Exception {
        String projectId = createProjectWithBaseUrl();
        byte[] excel = writeExcel(List.of(goodCaseWithBlankKeelPath()));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excelFile(excel))
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    public void executeRun_blankKeelPath_passesGateAndEnqueues() throws Exception {
        String projectId = createProjectWithBaseUrl();
        byte[] excel = writeExcel(List.of(goodCaseWithBlankKeelPath()));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .file(excelFile(excel))
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    private static ManualTestCase badPhoneFieldCase() {
        return new ManualTestCase(
                "TC_03",
                "Invalid password login with valid-looking phone number",
                "",
                "1. Open login\n2. Enter in the Phone field\n3. Enter in the Password field",
                "Error message 'Incorrect email or phone number' is shown",
                "", "", "", "", "AUTOMATE");
    }

    private static ManualTestCase goodCaseWithBlankKeelPath() {
        return new ManualTestCase(
                "TC_01",
                "Login",
                "",
                "1. Open login page",
                "Login page is shown",
                "", "", "", "", "");
    }

    private static MockMultipartFile excelFile(byte[] excel) {
        return new MockMultipartFile(
                "excel",
                "upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel);
    }

    private static byte[] writeExcel(List<ManualTestCase> cases) throws Exception {
        Path path = Files.createTempFile("excel-upload-gate-", ".xlsx");
        try {
            ManualTcExcelWriter.write(path, cases);
            return Files.readAllBytes(path);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private String createProjectWithBaseUrl() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Excel Upload Gate\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"" + BASE_URL + "\"}"))
                .andExpect(status().isOk());

        return projectId;
    }

    private void assertNoConvertJobs(String projectId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/jobs?projectId=" + projectId + "&kind=CONVERT")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray jobs = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertEquals(jobs.length(), 0, "bad upload must not create convert jobs");
    }

    private void assertNoExecuteJobs(String projectId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/jobs?projectId=" + projectId + "&kind=EXECUTE")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray jobs = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertEquals(jobs.length(), 0, "bad upload must not create execute jobs");
    }
}
