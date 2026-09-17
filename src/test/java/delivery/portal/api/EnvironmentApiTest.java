package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUserRepository;
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
import org.testng.annotations.Test;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:env-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-env-api",
        "delivery.work-dir=./target/test-delivery-work-env-api"
})
@AutoConfigureMockMvc
public class EnvironmentApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;

    @Test
    public void environmentRevisionsCompareWithoutChangingProjectStorage() throws Exception {
        String projectId = createProject();
        String tenantBefore = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        Path rootBefore = store.projectDiskRoot(projectId);
        MvcResult first = mockMvc.perform(post("/api/projects/" + projectId + "/environments")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"staging\",\"origin\":\"https://staging.example\",\"credentialProfileRef\":\"qa\",\"providerAllowlist\":\"ollama\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String rev1 = new JSONObject(first.getResponse().getContentAsString()).getString("id");
        MvcResult second = mockMvc.perform(post("/api/projects/" + projectId + "/environments")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"staging\",\"origin\":\"https://uat.example\",\"credentialProfileRef\":\"qa\",\"providerAllowlist\":\"ollama\",\"baseRevision\":\"" + rev1 + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String rev2 = new JSONObject(second.getResponse().getContentAsString()).getString("id");
        mockMvc.perform(get("/api/projects/" + projectId + "/environments/" + rev1 + "/diff/" + rev2)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed['origin.after']").value("https://uat.example"));
        Assert.assertEquals(store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId(), tenantBefore);
        Assert.assertEquals(store.projectDiskRoot(projectId), rootBefore);
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Env Project\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private Long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }
}
