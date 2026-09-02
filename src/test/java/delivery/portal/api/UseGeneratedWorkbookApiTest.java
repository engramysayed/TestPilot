package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.service.GeneratedWorkbookService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:usegeneratedwb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-usegeneratedwb",
        "delivery.work-dir=./target/test-delivery-work-usegeneratedwb"
})
@AutoConfigureMockMvc
public class UseGeneratedWorkbookApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GeneratedWorkbookService workbooks;

    @Test
    public void describeAndStartConversion_withGeneratedWorkbook() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase(
                        "TC_01", "Login", "", "1. Open login", "1. OK",
                        "P1", "", "", "", "AUTOMATE")
        ), "TEST", "unit");

        mockMvc.perform(get("/api/projects/" + projectId + "/generated-workbook")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.tcCount").value(1));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .param("useGenerated", "true")
                        .param("mode", "NEW")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    public void useGenerated_startsExecuteRun() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase(
                        "TC_EX", "Execute case", "", "1. Open page", "1. OK",
                        "P1", "", "", "", "EXECUTE")
        ), "TEST", "unit");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .param("useGenerated", "true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    public void useGenerated_withoutWorkbook_returnsConflict() throws Exception {
        String projectId = createProject();
        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .param("useGenerated", "true")
                        .param("mode", "NEW")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_GENERATED_WORKBOOK"));
    }

    @Test
    public void useGenerated_manualOnlyWorkbook_returnsSurfaceMismatchOnAutomate() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase(
                        "TC_M1", "Manual only", "", "1. Review", "1. OK",
                        "P1", "", "", "", "MANUAL"),
                new ManualTestCase(
                        "TC_M2", "Also manual", "", "1. Review", "1. OK",
                        "P1", "", "", "", "MANUAL")
        ), "TEST", "unit");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .param("useGenerated", "true")
                        .param("mode", "NEW")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SURFACE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("AUTOMATE")));
    }

    @Test
    public void useGenerated_automateOnlyWorkbook_isAcceptedOnExecute() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase(
                        "TC_A1", "Automate only", "", "1. Click", "1. OK",
                        "P1", "", "", "", "AUTOMATE")
        ), "TEST", "unit");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .param("useGenerated", "true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    public void useGenerated_manualOnlyWorkbook_returnsSurfaceMismatchOnExecute() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase(
                        "TC_M1", "Manual only", "", "1. Review", "1. OK",
                        "P1", "", "", "", "MANUAL")
        ), "TEST", "unit");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .param("useGenerated", "true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SURFACE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsStringIgnoringCase("manual")));
    }

    @Test
    public void updateGeneratedWorkbookKeelPath_returnsRefreshedCounts() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase(
                        "TC_01", "Automate", "", "1. Click", "1. OK",
                        "P1", "", "", "", "AUTOMATE"),
                new ManualTestCase(
                        "TC_02", "Execute", "", "1. Open", "1. OK",
                        "P1", "", "", "", "EXECUTE")
        ), "TEST", "unit");

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/rows")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"tcId\":\"TC_01\",\"keelPath\":\"MANUAL\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keelPathCounts.MANUAL").value(1))
                .andExpect(jsonPath("$.keelPathCounts.AUTOMATE").value(0))
                .andExpect(jsonPath("$.keelPathCounts.EXECUTE").value(1))
                .andExpect(jsonPath("$.csv").isNotEmpty())
                .andExpect(jsonPath("$.csv").value(org.hamcrest.Matchers.containsString("MANUAL")));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Generated WB\",\"baseUrl\":\"https://example.test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
