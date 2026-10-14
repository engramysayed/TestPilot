package delivery.portal.api;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:hunttriagemember;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.hunt.triage.enabled=true",
        "delivery.store-root=./target/test-delivery-store-hunttriagemember",
        "delivery.work-dir=./target/test-delivery-work-hunttriagemember"
})
@AutoConfigureMockMvc
public class HuntTriageMemberApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void memberUser() throws Exception {
        if (!users.existsByEmailIgnoreCase("member@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("member@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
        Path root = Path.of("./target/test-delivery-store-hunttriagemember");
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
    public void member_canGetButCannotPatchOrPromote() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Member Triage\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        String tenantId = store.getOwnedProject(projectId,
                users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId())
                .orElseThrow().getTenantId();
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-hunttriagemember"))
                .addMember(TenantId.parse(tenantId),
                        users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId(),
                        WorkspaceRole.MEMBER);

        String jobId = "hunt_member_job";
        Path dir = store.projectDiskRoot(projectId).resolve("hunt-runs").resolve(jobId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("bug-report.json"), """
                [{"bugId":"bug_m1","title":"Member visible","severity":"minor","admission":"ORACLE",
                  "status":"FIRST_PASS","toastObservedAfterSettle":false,
                  "reproduceSteps":[{"action":"click","target":"X"}],
                  "url":"https://example.test/","blockedLocators":[],"evidenceRefs":[]}]
                """);

        var member = httpBasic("member@testpilot.local", "MemberPass1!");
        String base = "/api/projects/" + projectId + "/hunt/" + jobId;

        mockMvc.perform(get(base + "/bugs")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + "/summary")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        mockMvc.perform(patch(base + "/bugs/bug_m1")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base + "/bugs/bug_m1/promote")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
