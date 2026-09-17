package delivery.portal.web;

import delivery.portal.PortalApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:phase6-ui;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-phase6-ui",
        "delivery.work-dir=./target/test-delivery-work-phase6-ui"
})
@AutoConfigureMockMvc
public class Phase6SettingsAndHuntMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void projectSettingsExposeEnvironmentAndUsagePanels() throws Exception {
        String projectId = createProject();
        String body = mockMvc.perform(get("/projects/" + projectId + "?tab=settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"env-profiles-panel\""), "environment settings panel missing");
        Assert.assertTrue(body.contains("function loadEnvironments"), "environment loader missing");
        Assert.assertTrue(body.contains("/environments"), "environment API wiring missing");
        Assert.assertTrue(body.contains("id=\"usage-budget-panel\""), "usage history panel missing");
        Assert.assertTrue(body.contains("function loadUsage"), "usage loader missing");
        Assert.assertTrue(body.contains("/usage"), "usage API wiring missing");
    }

    @Test
    public void bugHunterPageHasReviewEditAcceptFlow() throws Exception {
        String body = mockMvc.perform(get("/bug-hunter")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"hunt-promote-panel\""), "promote panel missing");
        Assert.assertTrue(body.contains("/promote"), "promote API wiring missing");
        Assert.assertTrue(body.contains("accept"), "explicit accept control missing");
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Phase6 UI\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }
}
