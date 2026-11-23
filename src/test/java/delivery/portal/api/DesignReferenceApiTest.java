package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.PortalStore;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:designrefapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-design-ref",
        "delivery.work-dir=./target/test-delivery-work-design-ref"
})
@AutoConfigureMockMvc
public class DesignReferenceApiTest extends AbstractTestNGSpringContextTests {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-design-ref");
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
                        .content("{\"name\":\"Design Ref Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void uploadListAndServe_pngReference() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "file", "ref.png", "image/png", TINY_PNG);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(file)
                        .param("tcId", "TC_001")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tcId").value("TC_001"))
                .andExpect(jsonPath("$.storedAs").value("TC_001.png"));

        MvcResult listRes = mockMvc.perform(get("/api/projects/" + projectId + "/design-references")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray list = new JSONArray(listRes.getResponse().getContentAsString());
        Assert.assertEquals(list.length(), 1);
        Assert.assertEquals(list.getJSONObject(0).getString("tcId"), "TC_001");

        Path stored = store.projectDiskRoot(projectId)
                .resolve("design-references/TC_001.png");
        Assert.assertTrue(Files.isRegularFile(stored));

        mockMvc.perform(get("/api/projects/" + projectId + "/design-references/TC_001")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(content().bytes(TINY_PNG));
    }

    @Test
    public void upload_rejectsNonImage() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(file)
                        .param("tcId", "TC_001")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    public void serve_unknownTc_returns404() throws Exception {
        String projectId = createProject();
        mockMvc.perform(get("/api/projects/" + projectId + "/design-references/TC_MISSING")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    public void uploadAndServe_stepReference() throws Exception {
        String projectId = createProject();
        MockMultipartFile tcFile = new MockMultipartFile(
                "file", "tc.png", "image/png", TINY_PNG);
        MockMultipartFile stepFile = new MockMultipartFile(
                "file", "step.png", "image/png", TINY_PNG);

        // Upload a TC-level reference and two step-level ones.
        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(tcFile)
                        .param("tcId", "TC_010")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("tc"));
        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(stepFile)
                        .param("tcId", "TC_010")
                        .param("stepIndex", "1")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("step"))
                .andExpect(jsonPath("$.stepIndex").value(1))
                .andExpect(jsonPath("$.storedAs").value("TC_010/step-001.png"));
        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(stepFile)
                        .param("tcId", "TC_010")
                        .param("stepIndex", "3")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated());

        // list returns the TC with both steps and the TC-level marker.
        MvcResult listRes = mockMvc.perform(get("/api/projects/" + projectId + "/design-references")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray list = new JSONArray(listRes.getResponse().getContentAsString());
        Assert.assertEquals(list.length(), 1);
        JSONObject row = list.getJSONObject(0);
        Assert.assertEquals(row.getString("tcId"), "TC_010");
        Assert.assertTrue(row.getBoolean("hasTcLevel"));
        JSONArray steps = row.getJSONArray("steps");
        Assert.assertEquals(steps.length(), 2);
        Assert.assertEquals(steps.getInt(0), 1);
        Assert.assertEquals(steps.getInt(1), 3);

        // Step file lives inside the per-TC subdirectory.
        Path stepOnDisk = store.projectDiskRoot(projectId)
                .resolve("design-references/TC_010/step-001.png");
        Assert.assertTrue(Files.isRegularFile(stepOnDisk));

        mockMvc.perform(get("/api/projects/" + projectId + "/design-references/TC_010/step/1")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(content().bytes(TINY_PNG));
        mockMvc.perform(get("/api/projects/" + projectId + "/design-references/TC_010/step/7")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void upload_rejectsStepIndexOutOfRange() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "file", "s.png", "image/png", TINY_PNG);
        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(file)
                        .param("tcId", "TC_X")
                        .param("stepIndex", "0")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
