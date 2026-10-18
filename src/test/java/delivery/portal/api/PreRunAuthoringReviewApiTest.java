package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.service.GeneratedWorkbookService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:prerun-authoring-review;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-prerun-review",
        "delivery.work-dir=./target/test-delivery-work-prerun-review",
        "delivery.llm-base-url=http://127.0.0.1:9"
})
@AutoConfigureMockMvc
public class PreRunAuthoringReviewApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GeneratedWorkbookService workbooks;

    @Test
    public void preRunReview_keelProvider_unreachableOllama_returnsProviderUnavailable() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. OK",
                        "P1", "auth", "", "user@test.com", "EXECUTE")
        ), "TEST", "unit");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/pre-run-authoring-review")
                        .param("useGenerated", "true")
                        .param("provider", "keel")
                        .param("model", "gemma4:e2b")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString(
                        "provider must be cursor or ollama"))));
    }

    @Test
    public void preRunReview_unknownProvider_returnsBadRequest() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. OK",
                        "P1", "auth", "", "", "EXECUTE")
        ), "TEST", "unit");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/pre-run-authoring-review")
                        .param("useGenerated", "true")
                        .param("provider", "openai")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("provider must be")));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pre-run review\",\"baseUrl\":\"https://example.test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
