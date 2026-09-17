package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:usage-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-usage-api",
        "delivery.work-dir=./target/test-delivery-work-usage-api"
})
@AutoConfigureMockMvc
public class UsageApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-usage-api");
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
    public void usageHistoryListsEstimatedReservationWithoutDoubleSpendOnRetry() throws Exception {
        String projectId = createProject();
        String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        JobRecord job = new JobRecord(
                "job_usage_1", projectId, adminId(), "NEW",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://staging.example/", "", "", false);
        job.setTenantId(tenantId);
        store.saveJob(job);
        store.saveJob(job);

        MvcResult res = mockMvc.perform(get("/api/projects/" + projectId + "/usage")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hardCapUnits").isNumber())
                .andExpect(jsonPath("$.warningUnits").isNumber())
                .andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertEquals(body.getLong("reservedUnits"), 1L);
        JSONArray usage = body.getJSONArray("usage");
        int matches = 0;
        for (int i = 0; i < usage.length(); i++) {
            JSONObject row = usage.getJSONObject(i);
            if ("job_usage_1".equals(row.getString("jobId"))) {
                matches++;
                Assert.assertEquals(row.getString("estimatedKind"), "ESTIMATED");
                Assert.assertEquals(row.getLong("estimatedUnits"), 1L);
            }
        }
        Assert.assertTrue(matches >= 1, "expected usage row for reserved job");
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usage Project\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }
}
