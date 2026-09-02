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

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:generatetcapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-generatetcapi",
        "delivery.work-dir=./target/test-delivery-work-generatetcapi"
})
@AutoConfigureMockMvc
public class GenerateTcApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TcGenerateService generate;

    @Test
    public void generateTcs_returnsRowsAndCounts() throws Exception {
        String projectId = createProject();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tcId", "TC_01");
        row.put("title", "Login");
        row.put("keelPath", "AUTOMATE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("rows", List.of(row));
        payload.put("coverageNotes", "Applied smoke.");
        payload.put("csv", "TC_ID,Title\n");
        payload.put("counts", Map.of("AUTOMATE", 1));

        when(generate.generate(eq(projectId), anyLong(), anyString(), eq(false), isNull())).thenReturn(payload);

        mockMvc.perform(post("/api/projects/" + projectId + "/generate-tcs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a user I want to login\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].tcId").value("TC_01"))
                .andExpect(jsonPath("$.counts.AUTOMATE").value(1));
    }

    @Test
    public void generateTcs_forwardsSelectedModel() throws Exception {
        String projectId = createProject();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("model", "qwen2.5:latest");
        payload.put("rows", List.of());
        payload.put("coverageNotes", "");
        payload.put("csv", "");
        payload.put("counts", Map.of());

        when(generate.generate(eq(projectId), anyLong(), anyString(), eq(false), eq("qwen2.5:latest")))
                .thenReturn(payload);

        mockMvc.perform(post("/api/projects/" + projectId + "/generate-tcs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stories":"As a user I want invalid login only",
                                 "options":{"reviewPass":false,"model":"qwen2.5:latest"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("qwen2.5:latest"));
    }

    @Test
    public void generateTcs_qualityGateFailure_returns400() throws Exception {
        String projectId = createProject();
        when(generate.generate(eq(projectId), anyLong(), anyString(), eq(false), isNull()))
                .thenThrow(GenerateQualityGate.failureException(
                        List.of("tcId 'TC_01': steps are blank after normalize")));

        mockMvc.perform(post("/api/projects/" + projectId + "/generate-tcs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a user I want to login\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUALITY_GATE"))
                .andExpect(jsonPath("$.message").value("tcId 'TC_01': steps are blank after normalize"));
    }

    @Test
    public void compareGenerate_returnsBothModelPayloads() throws Exception {
        String projectId = createProject();
        Map<String, Object> sideA = new LinkedHashMap<>();
        sideA.put("model", "gemma4:e2b");
        sideA.put("rows", List.of(Map.of("tcId", "TC_01", "keelPath", "AUTOMATE")));
        sideA.put("counts", Map.of("AUTOMATE", 1));
        Map<String, Object> sideB = new LinkedHashMap<>();
        sideB.put("model", "qwen2.5:latest");
        sideB.put("rows", List.of(
                Map.of("tcId", "TC_01", "keelPath", "AUTOMATE"),
                Map.of("tcId", "TC_02", "keelPath", "EXECUTE")
        ));
        sideB.put("counts", Map.of("AUTOMATE", 1, "EXECUTE", 1));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("modelA", sideA);
        payload.put("modelB", sideB);

        when(generate.compare(eq(projectId), anyLong(), anyString(), eq("gemma4:e2b"), eq("qwen2.5:latest")))
                .thenReturn(payload);

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/compare")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stories":"As a user I want to login",
                                 "modelA":"gemma4:e2b","modelB":"qwen2.5:latest"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelA.model").value("gemma4:e2b"))
                .andExpect(jsonPath("$.modelA.counts.AUTOMATE").value(1))
                .andExpect(jsonPath("$.modelB.model").value("qwen2.5:latest"))
                .andExpect(jsonPath("$.modelB.counts.EXECUTE").value(1));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Generate API\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
