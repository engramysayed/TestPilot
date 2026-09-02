package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.InviteService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalauth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.store-root=./target/test-delivery-store-auth",
        "delivery.work-dir=./target/test-delivery-work-auth"
})
@AutoConfigureMockMvc
public class AuthOwnershipTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private InviteService invites;
    @Autowired
    private PortalStore store;

    @BeforeMethod
    public void ensureSecondUser() {
        if (!users.existsByEmailIgnoreCase("user2@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("user2@testpilot.local");
            u.setPasswordHash(encoder.encode("UserTwoPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    @Test
    public void userCannotReadAnotherUsersProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Only\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");

        mockMvc.perform(get("/api/projects/" + projectId)
                        .with(httpBasic("user2@testpilot.local", "UserTwoPass1!")))
                .andExpect(status().isNotFound());
    }

    @Test
    public void apiPostRejectedWithoutTestPilotHeader() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Header\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void inviteAcceptCreatesLoginCapableUser() throws Exception {
        var created = invites.createInvite("invited-" + System.nanoTime() + "@testpilot.local",
                users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId(),
                false);
        var invite = created.invite();
        mockMvc.perform(post("/invite/" + invite.getToken())
                        .with(csrf())
                        .param("password", "InvitePass12"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/dashboard/stats")
                        .with(httpBasic(invite.getEmail(), "InvitePass12")))
                .andExpect(status().isOk());
    }

    private String createAdminProject(String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void userCannotAccessAnotherUsersCredentials() throws Exception {
        String projectId = createAdminProject("Admin Creds");
        mockMvc.perform(post("/api/projects/" + projectId + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"normal_user\",\"username\":\"alice\",\"password\":\"Secret1!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + projectId + "/credentials")
                        .with(httpBasic("user2@testpilot.local", "UserTwoPass1!")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/" + projectId + "/credentials")
                        .with(httpBasic("user2@testpilot.local", "UserTwoPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"intruder\",\"username\":\"x\",\"password\":\"x\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/projects/" + projectId + "/credentials/normal_user")
                        .with(httpBasic("user2@testpilot.local", "UserTwoPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void userCannotAccessAnotherUsersArtifacts() throws Exception {
        String projectId = createAdminProject("Admin Artifacts");
        Path projectRoot = store.projectDiskRoot(projectId);
        Files.createDirectories(projectRoot.resolve("versions"));
        Files.writeString(projectRoot.resolve("versions/v1.zip"), "fake-zip");

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts")
                        .with(httpBasic("user2@testpilot.local", "UserTwoPass1!")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/projects/" + projectId + "/artifacts")
                        .param("path", "versions/v1.zip")
                        .with(httpBasic("user2@testpilot.local", "UserTwoPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }
}
