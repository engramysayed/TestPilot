package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.ProjectCredentialRepository;
import delivery.portal.persistence.ProjectRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:adminuserdel;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.store-root=./target/test-delivery-store-admin-user-del",
        "delivery.work-dir=./target/test-delivery-work-admin-user-del"
})
@AutoConfigureMockMvc
public class AdminUserDeletionApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private ProjectCredentialRepository credentials;
    @Autowired
    private ProjectRepository projects;

    @Test
    public void adminUserDeletePurgesOwnedProjectsAndCredentials() throws Exception {
        String email = "doomed-" + System.nanoTime() + "@testpilot.local";
        PortalUser target = new PortalUser();
        target.setEmail(email);
        target.setPasswordHash(encoder.encode("UserPass123!"));
        target.setRole(PortalUser.Role.USER);
        target.setEnabled(true);
        users.save(target);

        var created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(email, "UserPass123!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Doomed\",\"baseUrl\":\"https://doomed.example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        mockMvc.perform(post("/api/projects/" + projectId + "/credentials")
                        .with(httpBasic(email, "UserPass123!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"doomed_user\",\"username\":\"u\",\"password\":\"secret\"}"))
                .andExpect(status().isCreated());
        Assert.assertFalse(credentials.findByProjectIdOrderByProfileNameAsc(projectId).isEmpty());

        mockMvc.perform(delete("/api/admin/users/" + target.getId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        Assert.assertTrue(users.findById(target.getId()).isEmpty());
        Assert.assertTrue(projects.findByProjectId(projectId).isEmpty(),
                "admin user delete must purge owned projects through the safe deletion lifecycle");
        Assert.assertTrue(credentials.findByProjectIdOrderByProfileNameAsc(projectId).isEmpty(),
                "admin user delete must purge credentials");
    }
}
