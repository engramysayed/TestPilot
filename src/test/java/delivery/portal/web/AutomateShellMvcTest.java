package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:automateshell;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-automateshell",
        "delivery.work-dir=./target/test-delivery-work-automateshell"
})
@AutoConfigureMockMvc
public class AutomateShellMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void automatePages_okWhenAuthenticated() throws Exception {
        for (String path : List.of("/automate", "/generate", "/execute")) {
            mockMvc.perform(get(path).with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    public void uploadPage_navActiveAndBackLink() throws Exception {
        String body = mockMvc.perform(get("/upload").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("href=\"/automate\">← Automate</a>"));
        Assert.assertTrue(body.contains("data-nav-group=\"automate\""));
        Assert.assertTrue(body.contains("is-open"));
        Assert.assertTrue(body.contains("href=\"/upload\" class=\"active\">Upload</a>"));
        Assert.assertTrue(body.contains("data-final-revise-enabled=\"false\""));
    }

    @Test
    public void jobsPage_navActive() throws Exception {
        String body = mockMvc.perform(get("/jobs").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("Part of Automate"));
        Assert.assertTrue(body.contains("data-nav-group=\"automate\""));
        Assert.assertTrue(body.contains("is-open"));
        Assert.assertTrue(body.contains("href=\"/jobs\" class=\"active\">Jobs</a>"));
    }

    @Test
    public void sidebar_hasAutomateGroup_notTopLevelUploadJobs() throws Exception {
        String body = mockMvc.perform(get("/automate").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("data-nav-group=\"automate\""));
        Assert.assertTrue(body.contains("nav-group-children"));
        Assert.assertTrue(body.contains("href=\"/upload\">Upload</a>"));
        Assert.assertTrue(body.contains("href=\"/jobs\">Jobs</a>"));

        String beforeAutomateGroup = body.substring(0, body.indexOf("data-nav-group=\"automate\""));
        Assert.assertFalse(beforeAutomateGroup.contains("href=\"/upload\""));
        Assert.assertFalse(beforeAutomateGroup.contains("href=\"/jobs\""));
    }

    @Test
    public void sidebar_hasJobActivityChip() throws Exception {
        String body = mockMvc.perform(get("/dashboard").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"sidebar-job-chip\""));
        Assert.assertTrue(body.contains("id=\"sidebar-job-chip-label\""));
    }
}
