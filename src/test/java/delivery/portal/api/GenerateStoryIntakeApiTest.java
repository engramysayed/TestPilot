package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import delivery.portal.PortalApplication;
import delivery.portal.service.TcGenerateService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Final story-intake on {@link GenerateTcController} (stub dropped in portal-enhancements pack 06).
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:storyintakefinal;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.generate.structured-intake=true",
        "delivery.store-root=./target/test-delivery-store-storyintakefinal",
        "delivery.work-dir=./target/test-delivery-work-storyintakefinal"
})
@AutoConfigureMockMvc
public class GenerateStoryIntakeApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TcGenerateService generate;

    @Test
    public void storyIntake_finalApi_mergesIntakeAndReturnsWorkbookPayload() throws Exception {
        String projectId = createProject();
        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("projectId", projectId);
        gen.put("csv", "TC_ID,Title\nTC_01,Apply coupon\n");
        gen.put("rows", List.of(Map.of("tcId", "TC_01", "title", "Apply coupon")));
        gen.put("suggestedKeelPath", "AUTOMATE");
        gen.put("alternatives", List.of("EXECUTE"));
        gen.put("coverageWarnings", List.of());
        when(generate.generate(eq(projectId), anyLong(), anyString(), anyBoolean(), any()))
                .thenReturn(gen);

        MvcResult res = mockMvc.perform(post("/api/projects/" + projectId + "/generate/story-intake")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Apply coupon",
                                  "acceptanceCriteria": ["Total drops", "Error on bad code"],
                                  "role": "Shopper",
                                  "persona": "Shopper",
                                  "surface": "Web checkout",
                                  "constraints": "Web checkout"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intake.role").value("Shopper"))
                .andExpect(jsonPath("$.suggestedKeelPath").value("AUTOMATE"))
                .andExpect(jsonPath("$.rows[0].tcId").value("TC_01"))
                .andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertFalse(body.optBoolean("stub", false), "Final API must not set stub:true");
        Assert.assertTrue(body.getString("storiesPrompt").contains("Apply coupon")
                        || body.getJSONObject("intake").getString("title").contains("Apply coupon"),
                "structured intake must reach Final controller");
        Assert.assertTrue(body.has("alternatives"));
        Assert.assertTrue(body.has("coverageWarnings"));
    }

    @Test
    public void storyIntake_qualityGate_returnsFullStructuredBody() throws Exception {
        String projectId = createProject();
        when(generate.generate(eq(projectId), anyLong(), anyString(), anyBoolean(), any()))
                .thenThrow(GenerateQualityGate.failureException(List.of(
                        "TC_ID 'TC_01': steps are blank — add clear steps")));

        MvcResult res = mockMvc.perform(post("/api/projects/" + projectId + "/generate/story-intake")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Login",
                                  "acceptanceCriteria": ["Valid user reaches home"],
                                  "role": "Member",
                                  "surface": "Web"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("code"), "QUALITY_GATE");
        Assert.assertEquals(body.getString("error"), "QUALITY_GATE");
        Assert.assertTrue(body.getJSONArray("errors").length() > 0);
        Assert.assertTrue(body.getJSONArray("suggestions").length() >= 0);
        JSONObject qg = body.getJSONObject("qualityGate");
        Assert.assertFalse(qg.getBoolean("passed"));
        Assert.assertTrue(qg.getJSONArray("failures").length() > 0);
        JSONObject failure0 = qg.getJSONArray("failures").getJSONObject(0);
        Assert.assertTrue(failure0.has("code") || failure0.has("message"));
        Assert.assertTrue(failure0.has("howToFix") || failure0.has("message"));
    }

    @Test
    public void storyIntake_shallowFreeText_rejected() throws Exception {
        String projectId = createProject();
        mockMvc.perform(post("/api/projects/" + projectId + "/generate/story-intake")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freeText\":\"do login stuff\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("SHALLOW_STORY"));
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intake Final\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }
}
