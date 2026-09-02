package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.ProjectCredentialService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:projectcreds;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-creds",
        "delivery.work-dir=./target/test-delivery-work-creds"
})
@AutoConfigureMockMvc
public class ProjectCredentialsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectCredentialService credentials;

    private String createProjectWithBaseUrl() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Creds Project\",\"baseUrl\":\"https://app.example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void createList_neverReturnsPassword() throws Exception {
        String id = createProjectWithBaseUrl();
        mockMvc.perform(post("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"normal_user\",\"username\":\"alice\",\"password\":\"Secret1!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileName").value("normal_user"))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].hasPassword").value(true))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    public void patch_blankPassword_keepsExisting() throws Exception {
        String id = createProjectWithBaseUrl();
        mockMvc.perform(post("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"normal_user\",\"username\":\"alice\",\"password\":\"Secret1!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/projects/" + id + "/credentials/normal_user")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        ProjectCredentialService.ResolvedCredential resolved = credentials.resolveForJob(id, "normal_user");
        Assert.assertEquals(resolved.username(), "bob");
        Assert.assertEquals(resolved.passwordPlain(), "Secret1!");
    }

    @Test
    public void duplicateProfile_returns409() throws Exception {
        String id = createProjectWithBaseUrl();
        mockMvc.perform(post("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"normal_user\",\"username\":\"alice\",\"password\":\"Secret1!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"normal_user\",\"username\":\"other\",\"password\":\"Other1!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_PROFILE"));
    }

    @Test
    public void delete_removesProfile() throws Exception {
        String id = createProjectWithBaseUrl();
        mockMvc.perform(post("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"temp_user\",\"username\":\"alice\",\"password\":\"Secret1!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/projects/" + id + "/credentials/temp_user")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(get("/api/projects/" + id + "/credentials")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
