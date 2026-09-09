package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.service.GeneratedWorkbookService;
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
import java.util.List;
import java.util.zip.ZipFile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:hunttest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-hunt",
        "delivery.work-dir=./target/test-delivery-work-hunt"
})
@AutoConfigureMockMvc
public class HuntApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GeneratedWorkbookService workbooks;

    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void clean() throws Exception {
        Path root = Path.of("./target/test-delivery-store-hunt");
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

    @Test
    public void startHunt_dryRunCompletesWithDownloadablePack() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "hunt-api");

        MvcResult start = mockMvc.perform(post("/api/projects/" + projectId + "/hunt-runs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tcIds\":[\"TC_01\"],\"userStory\":\"Break create user\",\"planner\":\"ollama\",\"scenarioCap\":5,\"cycleCeiling\":3}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobKind").value("HUNT"))
                .andReturn();
        String jobId = new JSONObject(start.getResponse().getContentAsString()).getString("jobId");

        String status = waitTerminal(jobId);
        Assert.assertEquals(status, "COMPLETED");

        MvcResult dl = mockMvc.perform(get("/api/jobs/" + jobId + "/download")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        Path tmp = Files.createTempFile("hunter-pack-", ".zip");
        Files.write(tmp, dl.getResponse().getContentAsByteArray());
        try (ZipFile zf = new ZipFile(tmp.toFile())) {
            Assert.assertNotNull(zf.getEntry("brief.md"));
            Assert.assertNotNull(zf.getEntry("SUMMARY.md"));
            Assert.assertNotNull(zf.getEntry("bug-report.json"));
            Assert.assertNotNull(zf.getEntry("candidate-scenarios.json"));
            Assert.assertNotNull(zf.getEntry("coverage-map.md"));
            Assert.assertNotNull(zf.getEntry("cycles/cycle-01/page-map.md"));
        }
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hunt Project\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    private String waitTerminal(String jobId) throws Exception {
        for (int i = 0; i < 80; i++) {
            MvcResult res = mockMvc.perform(get("/api/jobs/" + jobId)
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                            .header("X-Keel-Requested-With", "Keel"))
                    .andExpect(status().isOk())
                    .andReturn();
            String st = new JSONObject(res.getResponse().getContentAsString()).getString("status");
            if (!"QUEUED".equals(st) && !"RUNNING".equals(st)) {
                return st;
            }
            Thread.sleep(250);
        }
        Assert.fail("Hunt job did not complete: " + jobId);
        return null;
    }
}
