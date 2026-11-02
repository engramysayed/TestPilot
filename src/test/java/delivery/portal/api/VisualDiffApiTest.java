package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.PortalStore;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:visualdiffapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-visual-diff",
        "delivery.work-dir=./target/test-delivery-work-visual-diff"
})
@AutoConfigureMockMvc
public class VisualDiffApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-visual-diff");
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static byte[] png(Color fill, boolean redBox) throws Exception {
        BufferedImage i = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(fill);
        g.fillRect(0, 0, 100, 60);
        if (redBox) {
            g.setColor(Color.RED);
            g.fillRect(8, 8, 30, 20);
        }
        g.dispose();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(i, "png", o);
        return o.toByteArray();
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Visual Diff Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void api_roundTrip_baselineCompareApprove() throws Exception {
        String projectId = createProject();
        byte[] baseline = png(Color.WHITE, false);
        byte[] changed = png(Color.WHITE, true);

        MockMultipartFile baseFile = new MockMultipartFile("file", "baseline.png", "image/png", baseline);
        mockMvc.perform(multipart("/api/projects/" + projectId + "/visual-baselines/TC_HOME/desktop")
                        .file(baseFile)
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseKey").value("TC_HOME"))
                .andExpect(jsonPath("$.viewport").value("desktop"));

        mockMvc.perform(get("/api/projects/" + projectId + "/visual-baselines")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseKey").value("TC_HOME"));

        MockMultipartFile actual = new MockMultipartFile("file", "actual.png", "image/png", changed);
        MvcResult cmp = mockMvc.perform(multipart("/api/projects/" + projectId + "/visual-diffs/compare")
                        .file(actual)
                        .param("runId", "job_abc123")
                        .param("caseKey", "TC_HOME")
                        .param("viewport", "desktop")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andReturn();
        Assert.assertTrue(new JSONObject(cmp.getResponse().getContentAsString()).getInt("changedBlocks") > 0);

        mockMvc.perform(get("/api/projects/" + projectId + "/visual-diffs")
                        .param("pending", "true")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value("job_abc123"));

        mockMvc.perform(get("/api/projects/" + projectId + "/visual-diffs/job_abc123/TC_HOME/desktop/actual")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/" + projectId + "/visual-baselines/TC_HOME/desktop/approve")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"job_abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRunId").value("job_abc123"));

        MockMultipartFile again = new MockMultipartFile("file", "actual.png", "image/png", changed);
        mockMvc.perform(multipart("/api/projects/" + projectId + "/visual-diffs/compare")
                        .file(again)
                        .param("runId", "job_abc124")
                        .param("caseKey", "TC_HOME")
                        .param("viewport", "desktop")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        Path stored = store.projectDiskRoot(projectId)
                .resolve("visual-baselines/TC_HOME/desktop/baseline.png");
        Assert.assertTrue(Files.isRegularFile(stored));
    }
}
