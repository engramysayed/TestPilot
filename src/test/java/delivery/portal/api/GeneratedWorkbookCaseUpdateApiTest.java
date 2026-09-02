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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:genwb-case-update;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-genwb-case-update",
        "delivery.work-dir=./target/test-delivery-work-genwb-case-update"
})
@AutoConfigureMockMvc
public class GeneratedWorkbookCaseUpdateApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GeneratedWorkbookService workbooks;

    @Test
    public void updateCase_success() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. OK",
                        "P1", "auth", "", "user@test.com", "AUTOMATE")
        ), "TEST", "unit");

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/cases/TC_01")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated login",
                                  "preconditions": "Logged out",
                                  "steps": "1. Open login\\n2. Submit",
                                  "expectedResult": "1. OK\\n2. Redirect",
                                  "testData": "user@test.com",
                                  "priority": "P2",
                                  "tags": "smoke",
                                  "visualAssertion": "Form visible",
                                  "keelPath": "EXECUTE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.title").value("Updated login"))
                .andExpect(jsonPath("$.row.keelPath").value("EXECUTE"))
                .andExpect(jsonPath("$.keelPathCounts.EXECUTE").value(1))
                .andExpect(jsonPath("$.keelPathCounts.AUTOMATE").value(0))
                .andExpect(jsonPath("$.csv").value(org.hamcrest.Matchers.containsString("Updated login")));
    }

    @Test
    public void updateCase_qualityGateReject() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. OK",
                        "P1", "", "", "", "AUTOMATE")
        ), "TEST", "unit");

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/cases/TC_01")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Bad phone case",
                                  "preconditions": "",
                                  "steps": "1. Open login\\n2. Enter in the Phone field\\n3. Enter in the Password field",
                                  "expectedResult": "Error message 'Incorrect email or phone number' is shown",
                                  "testData": "",
                                  "priority": "P1",
                                  "tags": "",
                                  "visualAssertion": "",
                                  "keelPath": "EXECUTE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUALITY_GATE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsStringIgnoringCase("email or phone")));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Case Update\",\"baseUrl\":\"https://example.test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
