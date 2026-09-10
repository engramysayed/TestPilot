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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
        for (String path : List.of("/automate", "/generate", "/execute", "/runs")) {
            mockMvc.perform(get(path).with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    public void automatePage_isTheUploadForm() throws Exception {
        String body = mockMvc.perform(get("/automate").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("href=\"/automate\""), "Automate nav missing");
        Assert.assertTrue(body.contains("href=\"/runs\""), "Runs nav missing");
        Assert.assertFalse(body.contains("nav-group-children"));
        Assert.assertFalse(body.contains("href=\"/upload\">Upload</a>"));
        Assert.assertTrue(body.contains("id=\"pre-run-review\""), "review option missing");
        Assert.assertTrue(body.contains("data-final-revise-enabled=\"false\""));
    }

    @Test
    public void uploadRedirectsToAutomate() throws Exception {
        mockMvc.perform(get("/upload").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/automate"));
        mockMvc.perform(get("/upload").param("projectId", "prj_abc").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/automate?projectId=prj_abc"));
    }

    @Test
    public void runsPage_navActive() throws Exception {
        String body = mockMvc.perform(get("/runs").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains(">Runs</h1>") || body.contains(">Runs</a>"));
        Assert.assertTrue(body.contains("href=\"/runs\" class=\"active\">Runs</a>")
                || body.contains("href=\"/runs\""));
        Assert.assertFalse(body.contains("Part of Automate"));
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
