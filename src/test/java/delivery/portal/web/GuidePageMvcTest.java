package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:guidepage;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-guidepage",
        "delivery.work-dir=./target/test-delivery-work-guidepage"
})
@AutoConfigureMockMvc
public class GuidePageMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    private String fetchGuide() throws Exception {
        return mockMvc.perform(get("/guide").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    public void guidePage_returns200_withKeySectionAnchors() throws Exception {
        String body = fetchGuide();
        Assert.assertTrue(body.contains("id=\"generate\""), "Generate section");
        Assert.assertTrue(body.contains("id=\"execute\""), "Execute section");
        Assert.assertTrue(body.contains("id=\"automate\""), "Automate section");
        Assert.assertTrue(body.contains("id=\"hunt\""), "Hunt section");
        Assert.assertTrue(body.contains("id=\"credits\""), "Credits section");
        Assert.assertTrue(body.contains("id=\"roles\""), "Roles section");
        Assert.assertTrue(body.contains("id=\"mobile\""), "Mobile section");
        Assert.assertTrue(body.contains("id=\"quality-gate\""), "Quality gate section");
        Assert.assertTrue(body.contains("id=\"tc-authoring\""), "TC authoring section");
        Assert.assertTrue(body.contains(">Generate</h2>") || body.contains("<h2>Generate</h2>"), "Generate title");
        Assert.assertTrue(body.contains("<h2>Execute</h2>"), "Execute title");
        Assert.assertTrue(body.contains("<h2>Automate</h2>"), "Automate title");
        Assert.assertTrue(body.contains("Bug Hunt") || body.contains("Bug Hunter"), "Hunt title");
        Assert.assertTrue(body.contains("<h2>Credits</h2>"), "Credits title");
        Assert.assertTrue(body.contains("Roles"), "Roles title");
        Assert.assertTrue(body.contains("<h2>Mobile</h2>"), "Mobile title");
        Assert.assertTrue(body.contains("Quality gate"), "Quality gate title");
        Assert.assertTrue(body.contains("TC authoring") || body.contains("How to write test cases"), "TC authoring title");
        Assert.assertTrue(body.contains("CONFIRMED") && body.toLowerCase().contains("promote"),
                "must state CONFIRMED vs promote");
    }

    @Test
    public void sidebar_showsGuide_notSeparateTcGuideNav() throws Exception {
        String body = fetchGuide();
        Assert.assertTrue(body.contains("href=\"/guide\""), "Guide link in sidebar/page");
        Assert.assertTrue(body.contains(">Guide</span>") || body.contains(">Guide</a>"), "Guide label");
        Assert.assertFalse(body.contains("href=\"/tc-guide\""), "must not link Workspace nav to /tc-guide");
        Assert.assertFalse(body.contains("How to write tests</span>"), "old TC guide Workspace label removed");
    }

    @Test
    public void tcGuide_redirectsToGuideTcAuthoring() throws Exception {
        mockMvc.perform(get("/tc-guide").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/guide#tc-authoring"));
    }

    @Test
    public void guide_includesFlowDiagrams() throws Exception {
        String body = fetchGuide();
        Assert.assertTrue(body.contains("/img/guide/flow-generate-qg-import.svg"));
        Assert.assertTrue(body.contains("/img/guide/flow-hunt-triage.svg"));
        Assert.assertTrue(body.contains("/img/guide/flow-mobile-connect.svg"));
    }
}
