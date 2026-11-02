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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:knowledgepackapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-knowledge-pack",
        "delivery.work-dir=./target/test-delivery-work-knowledge-pack"
})
@AutoConfigureMockMvc
public class KnowledgePackApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-knowledge-pack");
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
                        .content("{\"name\":\"Knowledge Pack Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void uploadMd_list_get_roundTrip() throws Exception {
        String projectId = createProject();
        String md = "# Pack body\n\nGuidance for generate-from-knowledge.\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-pack.md", "text/markdown", md.getBytes(StandardCharsets.UTF_8));

        MvcResult createRes = mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/knowledge-packs/ingest/upload-md")
                        .file(file)
                        .param("title", "Sample MD Pack")
                        .param("batchSize", "40")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Sample MD Pack"))
                .andExpect(jsonPath("$.source").value("UPLOAD_MD"))
                .andExpect(jsonPath("$.batchSize").value(40))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.markdown").value(md))
                .andReturn();

        JSONObject created = new JSONObject(createRes.getResponse().getContentAsString());
        String packId = created.getString("packId");
        Assert.assertTrue(packId.startsWith("kp_"));

        Path storedMd = store.projectDiskRoot(projectId)
                .resolve("knowledge-packs").resolve(packId).resolve("pack.md");
        Assert.assertTrue(Files.isRegularFile(storedMd));
        Assert.assertEquals(Files.readString(storedMd), md);

        MvcResult listRes = mockMvc.perform(get("/api/projects/" + projectId + "/knowledge-packs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray list = new JSONArray(listRes.getResponse().getContentAsString());
        Assert.assertEquals(list.length(), 1);
        Assert.assertEquals(list.getJSONObject(0).getString("packId"), packId);
        Assert.assertEquals(list.getJSONObject(0).getString("markdown"), "");

        mockMvc.perform(get("/api/projects/" + projectId + "/knowledge-packs/" + packId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packId").value(packId))
                .andExpect(jsonPath("$.markdown").value(md))
                .andExpect(jsonPath("$.source").value("UPLOAD_MD"));
    }

    @Test
    public void patch_bumpsRevision() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.md", "text/markdown", "# A\n".getBytes(StandardCharsets.UTF_8));
        MvcResult createRes = mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/knowledge-packs/ingest/upload-md")
                        .file(file)
                        .param("batchSize", "30")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated())
                .andReturn();
        String packId = new JSONObject(createRes.getResponse().getContentAsString()).getString("packId");

        mockMvc.perform(patch("/api/projects/" + projectId + "/knowledge-packs/" + packId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\",\"batchSize\":55,\"markdown\":\"# Updated body\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.batchSize").value(55))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.markdown").value("# Updated body\n"));
    }

    @Test
    public void uploadMd_rejectsEmptyFile() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.md", "text/markdown", new byte[0]);
        mockMvc.perform(multipart(
                        "/api/projects/" + projectId + "/knowledge-packs/ingest/upload-md")
                        .file(file)
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
