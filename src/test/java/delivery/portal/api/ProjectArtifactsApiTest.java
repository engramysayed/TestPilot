package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.PortalStore;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:projectartifacts;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-artifacts",
        "delivery.work-dir=./target/test-delivery-work-artifacts"
})
@AutoConfigureMockMvc
public class ProjectArtifactsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-artifacts");
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

    private String createProjectWithFixture() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Artifacts Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
        seedFixture(projectId);
        return projectId;
    }

    private void seedFixture(String projectId) throws Exception {
        Path projectRoot = store.projectDiskRoot(projectId);
        Files.createDirectories(projectRoot.resolve("versions"));
        Files.writeString(projectRoot.resolve("versions/v1.zip"), "fake-zip-content");
        Path loginPage = projectRoot.resolve(
                "framework/src/main/java/project/pages/LoginPage.java");
        Files.createDirectories(loginPage.getParent());
        Files.writeString(loginPage, "public class LoginPage {}");
        Path generatedTest = projectRoot.resolve(
                "framework/src/test/java/project/tests/generated/TC_01.java");
        Files.createDirectories(generatedTest.getParent());
        Files.writeString(generatedTest, "public class TC_01 {}");
        Files.createDirectories(projectRoot.resolve("evidence"));
        Files.writeString(projectRoot.resolve("evidence/hidden.png"), "png-bytes");
        Files.createDirectories(projectRoot.resolve("ir"));
        Files.writeString(projectRoot.resolve("ir/draft.json"), "{}");
    }

    @Test
    public void listArtifacts_allowlistedOnly() throws Exception {
        String id = createProjectWithFixture();
        mockMvc.perform(get("/api/projects/" + id + "/artifacts")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packages[0].path").value("versions/v1.zip"))
                .andExpect(jsonPath("$.packages[0].label").value("v1"))
                .andExpect(jsonPath("$.pages[0].label").value("LoginPage"))
                .andExpect(jsonPath("$.tests.generated[0].label").value("TC_01_Generated"))
                .andExpect(jsonPath("$.tests.todo").isEmpty());
    }

    @Test
    public void delete_denyEvidencePath() throws Exception {
        String id = createProjectWithFixture();
        mockMvc.perform(delete("/api/projects/" + id + "/artifacts")
                        .param("path", "evidence/hidden.png")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_PATH"));
    }

    @Test
    public void delete_allowlistedZip() throws Exception {
        String id = createProjectWithFixture();
        Path zip = store.projectDiskRoot(id).resolve("versions/v1.zip");
        Assert.assertTrue(Files.isRegularFile(zip));

        mockMvc.perform(delete("/api/projects/" + id + "/artifacts")
                        .param("path", "versions/v1.zip")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        Assert.assertFalse(Files.exists(zip));
    }

    @Test
    public void preview_javaFile() throws Exception {
        String id = createProjectWithFixture();
        mockMvc.perform(get("/api/projects/" + id + "/artifacts/preview")
                        .param("path", "framework/src/main/java/project/pages/LoginPage.java")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(content().string("public class LoginPage {}"));
    }

    @Test
    public void download_zipOnly() throws Exception {
        String id = createProjectWithFixture();
        mockMvc.perform(get("/api/projects/" + id + "/artifacts/download")
                        .param("path", "versions/v1.zip")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("v1.zip")));
    }
}
