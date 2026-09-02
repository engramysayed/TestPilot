package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.TcImportService;
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
        "spring.datasource.url=jdbc:h2:mem:generateimportapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-generateimportapi",
        "delivery.work-dir=./target/test-delivery-work-generateimportapi"
})
@AutoConfigureMockMvc
public class GenerateImportApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TcImportService tcImport;

    @Test
    public void importJson_returnsOkPayload() throws Exception {
        String projectId = createProject();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tcId", "TC_01");
        row.put("title", "Login");
        row.put("keelPath", "AUTOMATE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("rows", List.of(row));
        payload.put("csv", "TC_ID,Title\n");
        payload.put("counts", Map.of("AUTOMATE", 1));

        when(tcImport.importRaw(eq(projectId), anyLong(), anyString(), eq("auto"), eq("PASTE_IMPORT")))
                .thenReturn(payload);

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/import")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "raw": "{\\"testCases\\":[{\\"tcId\\":\\"TC_01\\",\\"title\\":\\"Login\\"}]}",
                                  "format": "auto"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].tcId").value("TC_01"))
                .andExpect(jsonPath("$.counts.AUTOMATE").value(1));
    }

    @Test
    public void importEmpty_returnsBadRequest() throws Exception {
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/import")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raw\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Import API\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
