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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:hunttriageapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.hunt.triage.enabled=true",
        "delivery.store-root=./target/test-delivery-store-hunttriageapi",
        "delivery.work-dir=./target/test-delivery-work-hunttriageapi"
})
@AutoConfigureMockMvc
public class HuntTriageApiTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private GeneratedWorkbookService workbooks;

    @BeforeMethod
    public void clean() throws Exception {
        Path root = Path.of("./target/test-delivery-store-hunttriageapi");
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
    public void triage_listSummaryPatchPromote_confirmedDoesNotWriteLibrary() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Baseline", "", "1. Open", "ok", "P1", "smoke")
        ), "test", "hunt-triage");
        int before = workbooks.readCases(projectId).size();

        String jobId = "hunt_triage_job1";
        seedBugReport(projectId, jobId);
        String base = "/api/projects/" + projectId + "/hunt/" + jobId;

        MvcResult list = mockMvc.perform(get(base + "/bugs")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject listed = new JSONObject(list.getResponse().getContentAsString());
        JSONArray bugs = listed.getJSONArray("bugs");
        Assert.assertTrue(bugs.length() >= 2);
        JSONObject first = bugs.getJSONObject(0);
        Assert.assertTrue(first.has("bugId"));
        Assert.assertTrue(first.has("fingerprint"));
        Assert.assertTrue(first.has("admission"));
        Assert.assertTrue(first.has("status"));
        Assert.assertTrue(first.has("reproduceSteps"));
        Assert.assertTrue(first.has("toastObservedAfterSettle"));
        String bugId = first.getString("bugId");

        MvcResult summary = mockMvc.perform(get(base + "/summary")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject sum = new JSONObject(summary.getResponse().getContentAsString());
        Assert.assertTrue(sum.has("firstPass"));
        Assert.assertTrue(sum.has("flaky"));
        Assert.assertTrue(sum.has("confirmed"));
        Assert.assertTrue(sum.has("dismissed"));
        Assert.assertTrue(sum.has("coverageUrls"));
        Assert.assertTrue(sum.has("plateau"));

        // Promote before confirm → conflict; library unchanged
        mockMvc.perform(post(base + "/bugs/" + bugId + "/promote")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
        Assert.assertEquals(workbooks.readCases(projectId).size(), before);

        mockMvc.perform(patch(base + "/bugs/" + bugId)
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"note\":\"repro ok\"}"))
                .andExpect(status().isOk());

        // CONFIRMED alone must not grow the library
        Assert.assertEquals(workbooks.readCases(projectId).size(), before,
                "CONFIRMED must not write library rows");

        // Refresh-safe: status persists via triage-state.json
        MvcResult listed2 = mockMvc.perform(get(base + "/bugs")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray bugs2 = new JSONObject(listed2.getResponse().getContentAsString()).getJSONArray("bugs");
        boolean foundConfirmed = false;
        for (int i = 0; i < bugs2.length(); i++) {
            JSONObject b = bugs2.getJSONObject(i);
            if (bugId.equals(b.getString("bugId"))) {
                Assert.assertEquals(b.getString("status"), "CONFIRMED");
                Assert.assertEquals(b.optString("note"), "repro ok");
                foundConfirmed = true;
            }
        }
        Assert.assertTrue(foundConfirmed);

        MvcResult promoted = mockMvc.perform(post(base + "/bugs/" + bugId + "/promote")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        JSONObject body = new JSONObject(promoted.getResponse().getContentAsString());
        Assert.assertTrue(body.getString("tcDraftId").startsWith("TC_DRAFT_"));
        Assert.assertEquals(workbooks.readCases(projectId).size(), before + 1);

        // Dismiss second bug + project alias
        String bug2 = bugs.getJSONObject(1).getString("bugId");
        mockMvc.perform(patch(base + "/bugs/" + bug2)
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());

        MvcResult alias = mockMvc.perform(get("/api/projects/" + projectId + "/hunt-bugs")
                        .param("status", "DISMISSED")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray aliasBugs = new JSONObject(alias.getResponse().getContentAsString()).getJSONArray("bugs");
        Assert.assertTrue(aliasBugs.length() >= 1);
        Assert.assertEquals(aliasBugs.getJSONObject(0).getString("jobId"), jobId);
        Assert.assertEquals(aliasBugs.getJSONObject(0).getString("status"), "DISMISSED");
    }

    private void seedBugReport(String projectId, String jobId) throws Exception {
        Path dir = store.projectDiskRoot(projectId).resolve("hunt-runs").resolve(jobId);
        Files.createDirectories(dir);
        String json = """
                [
                  {
                    "bugId": "bug_demo_1",
                    "tcId": "HUNT_01",
                    "title": "Checkout total does not update after coupon",
                    "severity": "major",
                    "admission": "ORACLE",
                    "status": "FIRST_PASS",
                    "toastObservedAfterSettle": true,
                    "reproduceSteps": [
                      {"action": "click", "target": "Apply coupon"},
                      {"action": "type", "target": "Coupon code", "value": "SAVE10"}
                    ],
                    "url": "https://example.test/checkout",
                    "blockedLocators": [],
                    "evidenceRefs": ["screenshots/bug_demo_1.png"],
                    "expected": "Total updates"
                  },
                  {
                    "bugId": "bug_demo_2",
                    "tcId": "HUNT_02",
                    "title": "Save button stays disabled",
                    "severity": "minor",
                    "admission": "MAP_BOUND_ASSERT",
                    "status": "FLAKY",
                    "toastObservedAfterSettle": false,
                    "dupCount": 2,
                    "reproduceSteps": [{"action": "click", "target": "Save"}],
                    "url": "https://example.test/form",
                    "blockedLocators": [],
                    "evidenceRefs": [],
                    "expected": "Save enabled"
                  }
                ]
                """;
        Files.writeString(dir.resolve("bug-report.json"), json);
        Files.writeString(dir.resolve("SUMMARY.md"), "# Bug Hunter summary\n- Stop: NO_PROGRESS\n");
        Files.writeString(dir.resolve("coverage-map.json"),
                "{\"urls\":[\"https://example.test/checkout\",\"https://example.test/form\"],\"fingerprints\":[]}");
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Triage API\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }
}
