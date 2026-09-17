package delivery.portal.api.v1;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.service.GeneratedWorkbookService;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:public-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-public-api",
        "delivery.work-dir=./target/test-delivery-work-public-api"
})
@AutoConfigureMockMvc
public class PublicJobApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GeneratedWorkbookService workbooks;

    @Test
    public void serviceTokenWhoamiIdempotentSubmitAndRevoke() throws Exception {
        String projectId = createProject();
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")
        ), "test", "public-api");

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JSONObject svc = new JSONObject(created.getResponse().getContentAsString());
        String token = svc.getString("token");
        String serviceId = svc.getString("id");
        Assert.assertTrue(token.startsWith("tp_svc_"));

        mockMvc.perform(get("/api/v1/whoami")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.serviceId").value(serviceId));

        MvcResult first = mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .header("Idempotency-Key", "ci-build-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = new JSONObject(first.getResponse().getContentAsString()).getString("jobId");
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel")
                        .header("Idempotency-Key", "ci-build-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(get("/api/v1/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/projects/" + projectId + "/services/" + serviceId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/whoami")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnauthorized());
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CI Project\",\"baseUrl\":\"https://ci.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }
}
