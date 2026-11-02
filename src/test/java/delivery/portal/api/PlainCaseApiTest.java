package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.PortalStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:plaincaseapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-plain-case",
        "delivery.work-dir=./target/test-delivery-work-plain-case"
})
@AutoConfigureMockMvc
public class PlainCaseApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-plain-case");
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Plain Case Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void create_list_get_patch_roundTrip() throws Exception {
        String projectId = createProject();

        MvcResult createRes = mockMvc.perform(post("/api/projects/" + projectId + "/plain-cases")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Login smoke\",\"lines\":[\"click Login\",\"expect Welcome\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Login smoke"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.caseId").exists())
                .andExpect(jsonPath("$.lines[0]").value("click Login"))
                .andExpect(jsonPath("$.lines[1]").value("expect Welcome"))
                .andReturn();

        JSONObject created = new JSONObject(createRes.getResponse().getContentAsString());
        String caseId = created.getString("id");
        Assert.assertTrue(caseId.startsWith("pc_"));

        Path stored = store.projectDiskRoot(projectId).resolve("plain-cases").resolve(caseId + ".json");
        Assert.assertTrue(Files.isRegularFile(stored));

        MvcResult listRes = mockMvc.perform(get("/api/projects/" + projectId + "/plain-cases")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray list = new JSONArray(listRes.getResponse().getContentAsString());
        Assert.assertEquals(list.length(), 1);
        Assert.assertEquals(list.getJSONObject(0).getString("id"), caseId);

        mockMvc.perform(get("/api/projects/" + projectId + "/plain-cases/" + caseId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId))
                .andExpect(jsonPath("$.title").value("Login smoke"));

        mockMvc.perform(patch("/api/projects/" + projectId + "/plain-cases/" + caseId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Login v2\",\"lines\":[\"click Sign in\",\"expect Dashboard\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Login v2"))
                .andExpect(jsonPath("$.lines[0]").value("click Sign in"));
    }

    @Test
    public void create_rejectsEmptyLines() throws Exception {
        String projectId = createProject();
        mockMvc.perform(post("/api/projects/" + projectId + "/plain-cases")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"lines\":[\"  \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    public void execute_queuesJob_fromPlainCase() throws Exception {
        String projectId = createProject();
        MvcResult createRes = mockMvc.perform(post("/api/projects/" + projectId + "/plain-cases")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Exec wire\",\"lines\":[\"1. Open home\",\"expect Home\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String caseId = new JSONObject(createRes.getResponse().getContentAsString()).getString("id");

        mockMvc.perform(post("/api/projects/" + projectId + "/plain-cases/" + caseId + "/execute")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.plainCaseId").value(caseId))
                .andExpect(jsonPath("$.source").value("plain-case"));
    }
}
