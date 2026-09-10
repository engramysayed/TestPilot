package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import delivery.portal.PortalApplication;
import delivery.portal.service.AuthoringReviewService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:authoringreviewapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-authoringreviewapi",
        "delivery.work-dir=./target/test-delivery-work-authoringreviewapi"
})
@AutoConfigureMockMvc
public class AuthoringReviewApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthoringReviewService reviews;

    @Test
    public void authoringReview_returnsProposalWithGateErrors_withoutApplyingIt() throws Exception {
        String projectId = createProject();
        Map<String, Object> payload = Map.of(
                "provider", "cursor",
                "findings", List.of(Map.of(
                        "severity", "warning",
                        "tcId", "TC_01",
                        "message", "Expected result is vague")),
                "csv", "TC_ID,Title\nTC_01,Login\n",
                "coverageNotes", "Add locked-user coverage.",
                "gateErrors", List.of("tcId 'TC_01': expected result is vague"),
                "tcCount", 1,
                "previewOk", false);
        when(reviews.review(
                eq(projectId),
                anyLong(),
                eq("cursor"),
                eq(""),
                eq("Keep the suite focused"),
                eq("A user can log in")))
                .thenReturn(payload);

        performReview(projectId, """
                {
                  "provider": "cursor",
                  "requirementsNotes": "Keep the suite focused",
                  "stories": "A user can log in"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("cursor"))
                .andExpect(jsonPath("$.findings[0].tcId").value("TC_01"))
                .andExpect(jsonPath("$.csv").value("TC_ID,Title\nTC_01,Login\n"))
                .andExpect(jsonPath("$.gateErrors[0]").value("tcId 'TC_01': expected result is vague"))
                .andExpect(jsonPath("$.previewOk").value(false));
    }

    @Test
    public void authoringReview_badProvider_returnsBadRequest() throws Exception {
        String projectId = createProject();
        when(reviews.review(eq(projectId), anyLong(), eq("openai"), eq(""), eq(""), eq("")))
                .thenThrow(new IllegalArgumentException("provider must be cursor or ollama"));

        performReview(projectId, """
                {"provider":"openai","requirementsNotes":"","stories":""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("provider must be cursor or ollama"));
    }

    @Test
    public void authoringReview_qualityGateException_returnsQualityGateError() throws Exception {
        String projectId = createProject();
        when(reviews.review(eq(projectId), anyLong(), eq("ollama"), eq("gemma4:e2b"), eq(""), eq("")))
                .thenThrow(GenerateQualityGate.failureException(
                        List.of("tcId 'TC_01': steps are blank after normalize")));

        performReview(projectId, """
                {"provider":"ollama","model":"gemma4:e2b","requirementsNotes":"","stories":""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUALITY_GATE"))
                .andExpect(jsonPath("$.message")
                        .value("tcId 'TC_01': steps are blank after normalize"));
    }

    @Test
    public void authoringReview_missingWorkbook_returnsNotFound() throws Exception {
        String projectId = createProject();
        when(reviews.review(eq(projectId), anyLong(), eq("ollama"), eq("gemma4:e2b"), eq(""), eq("")))
                .thenThrow(new IllegalStateException("NO_GENERATED_WORKBOOK"));

        performReview(projectId, """
                {"provider":"ollama","model":"gemma4:e2b","requirementsNotes":"","stories":""}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NO_GENERATED_WORKBOOK"));
    }

    @Test
    public void authoringReview_providerFailure_returnsServiceUnavailable() throws Exception {
        String projectId = createProject();
        when(reviews.review(eq(projectId), anyLong(), eq("cursor"), eq(""), eq(""), eq("")))
                .thenThrow(new IllegalStateException("cursor returned empty review"));

        performReview(projectId, """
                {"provider":"cursor","requirementsNotes":"","stories":""}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("cursor returned empty review"));
    }

    private org.springframework.test.web.servlet.ResultActions performReview(
            String projectId,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/projects/" + projectId + "/generate/authoring-review")
                .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                .header("X-Keel-Requested-With", "Keel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createProject() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Authoring Review API\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(result.getResponse().getContentAsString()).getString("projectId");
    }
}
