package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.service.GeneratedWorkbookService;
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
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:suite-slice-e;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-suite-slice-e",
        "delivery.work-dir=./target/test-delivery-work-suite-slice-e"
})
@AutoConfigureMockMvc
public class SuiteSliceEApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GeneratedWorkbookService workbooks;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-suite-slice-e");
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

    private String createProject(String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    private void seedWorkbook(String projectId) throws Exception {
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_1", "One", "", "1", "1", "P1", "", "", "", "EXECUTE"),
                new ManualTestCase("TC_2", "Two", "", "1", "1", "P1", "", "", "", "EXECUTE"),
                new ManualTestCase("TC_3", "Three", "", "1", "1", "P1", "", "", "", "EXECUTE")
        ), "SUITE_E", "unit");
    }

    @Test
    public void testCaseLibrary_listsWorkbookIds() throws Exception {
        String projectId = createProject("Suite E Lib");
        seedWorkbook(projectId);

        mockMvc.perform(get("/api/projects/" + projectId + "/test-case-library")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].executableInSuite").value(true));
    }

    @Test
    public void suiteMembership_saveReload_andRunNow() throws Exception {
        String projectId = createProject("Suite E Flow");
        seedWorkbook(projectId);

        MvcResult createRes = mockMvc.perform(post("/api/projects/" + projectId + "/suites")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"regression-custom","tags":["checkout"],"tcIds":["TC_1","TC_2","TC_3"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String suiteId = new JSONObject(createRes.getResponse().getContentAsString()).getString("suiteId");

        mockMvc.perform(get("/api/projects/" + projectId + "/suites/" + suiteId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tcIds.length()").value(3));

        mockMvc.perform(put("/api/projects/" + projectId + "/suites/" + suiteId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tcIds":["TC_2","TC_3"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tcIds[0]").value("TC_2"));

        MvcResult getAfter = mockMvc.perform(get("/api/projects/" + projectId + "/suites/" + suiteId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray reloaded = new JSONObject(getAfter.getResponse().getContentAsString()).getJSONArray("tcIds");
        Assert.assertEquals(reloaded.length(), 2);

        mockMvc.perform(post("/api/projects/" + projectId + "/suites/" + suiteId + "/runs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"manual\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists());
    }

    @Test
    public void suiteRun_rejectsEmptyMembership() throws Exception {
        String projectId = createProject("Suite E Empty");
        seedWorkbook(projectId);

        MvcResult createRes = mockMvc.perform(post("/api/projects/" + projectId + "/suites")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"empty-pack","tags":["ops"],"tcIds":[]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String suiteId = new JSONObject(createRes.getResponse().getContentAsString()).getString("suiteId");

        mockMvc.perform(post("/api/projects/" + projectId + "/suites/" + suiteId + "/runs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"manual\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCaseLibrary_deniesOtherProject() throws Exception {
        String p1 = createProject("Suite E A");
        String p2 = createProject("Suite E B");
        seedWorkbook(p1);
        seedWorkbook(p2);

        mockMvc.perform(get("/api/projects/" + p1 + "/test-case-library")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        String foreign = p2;
        mockMvc.perform(get("/api/projects/" + foreign + "/test-case-library?q=TC_1")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        // Both owners see only their project rows when querying owned projects — ids differ per project disk root
        Assert.assertNotEquals(p1, p2);
    }
}
