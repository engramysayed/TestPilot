package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:lib-rev-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-lib-rev-api",
        "delivery.work-dir=./target/test-delivery-work-lib-rev-api"
})
@AutoConfigureMockMvc
public class LibraryRevisionApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GeneratedWorkbookService workbooks;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void memberUser() {
        if (!users.existsByEmailIgnoreCase("member@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("member@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    @Test
    public void historyDiffRestoreAndStaleEditAreEnforced() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(tc("TC_01", "First")), "GENERATE", "admin@testpilot.local");
        String first = describeRevisionId(projectId);
        workbooks.saveFromCases(projectId, List.of(tc("TC_01", "Second"), tc("TC_02", "Added")),
                "EDIT", "admin@testpilot.local", null, first);
        String second = describeRevisionId(projectId);

        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions.length()").value(2))
                .andExpect(jsonPath("$.revisions[0].author").value("admin@testpilot.local"))
                .andExpect(jsonPath("$.revisions[1].source").value("EDIT"));

        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions/" + first)
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].title").value("First"));

        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions/" + first + "/diff/" + second)
                        .param("kind", "added")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added[0]").value("TC_02"))
                .andExpect(jsonPath("$.changed").isEmpty());

        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions/" + first + "/diff/" + second)
                        .param("kind", "changed")
                        .param("field", "Title")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed.TC_01['Title.after']").value("Second"));

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/cases/TC_01")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stale\",\"steps\":\"1. Open page\",\"expectedResult\":\"Page opens\",\"baseRevision\":\""
                                + first + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LIBRARY_CONFLICT"));

        mockMvc.perform(post("/api/projects/" + projectId + "/library/revisions/" + first + "/restore")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].title").value("First"))
                .andExpect(jsonPath("$.source").value(org.hamcrest.Matchers.startsWith("restore:")));
    }

    @Test
    public void memberCanReviewHistoryButCannotRestore() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(tc("TC_01", "Owned")), "GENERATE", "admin@testpilot.local");
        String rev = describeRevisionId(projectId);
        TenantId tenant = TenantId.parse(store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId());
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-lib-rev-api"))
                .addMember(tenant, memberId(), WorkspaceRole.MEMBER);

        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions")
                        .with(member())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions/" + rev)
                        .with(member())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/" + projectId + "/library/revisions/" + rev + "/restore")
                        .with(member())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void startedJobKeepsPinnedRevisionAfterLaterEdit() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(tc("TC_01", "Pinned")), "GENERATE", "admin@testpilot.local");
        String pinned = describeRevisionId(projectId);

        MvcResult started = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .param("useGenerated", "true")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(started.getResponse().getContentAsString()).getString("jobId");

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libraryRevisionId").value(pinned));

        workbooks.saveFromCases(projectId, List.of(tc("TC_01", "Later")), "EDIT", "admin@testpilot.local", null, pinned);

        mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions/" + pinned)
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].title").value("Pinned"));
        JSONArray revisions = new JSONObject(mockMvc.perform(get("/api/projects/" + projectId + "/library/revisions")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).getJSONArray("revisions");
        Assert.assertTrue(revisions.length() >= 2);
    }

    private static ManualTestCase tc(String id, String title) {
        return new ManualTestCase(id, title, "", "1. Open page", "Page opens", "P1", "smoke", "", "", "AUTOMATE");
    }

    private String describeRevisionId(String projectId) throws Exception {
        return String.valueOf(workbooks.describe(projectId).orElseThrow().get("revisionId"));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(admin())
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Library History\",\"baseUrl\":\"https://example.test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin@testpilot.local", "ChangeMeAdmin1!");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor member() {
        return httpBasic("member@testpilot.local", "MemberPass1!");
    }

    private Long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private Long memberId() {
        return users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId();
    }
}
