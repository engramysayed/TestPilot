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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:guidemotion;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-guidemotion",
        "delivery.work-dir=./target/test-delivery-work-guidemotion"
})
@AutoConfigureMockMvc
public class GuideMotionMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    private String fetchGuideBody() throws Exception {
        return mockMvc.perform(get("/tc-guide").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    public void guidePage_renders() throws Exception {
        fetchGuideBody();
    }

    @Test
    public void guide_hasScrollSpyMarkup() throws Exception {
        String body = fetchGuideBody();
        Assert.assertTrue(body.contains("data-guide-section"), "missing scroll-spy section hook");
        Assert.assertTrue(body.contains("guide-toc"), "missing TOC markup");
    }

    @Test
    public void guide_automateLinksNotUploadHero() throws Exception {
        String body = fetchGuideBody();

        int heroStart = body.indexOf("class=\"page-title guide-hero");
        Assert.assertTrue(heroStart >= 0, "guide hero missing");
        int heroEnd = body.indexOf("class=\"guide-layout\"", heroStart);
        Assert.assertTrue(heroEnd > heroStart, "guide layout missing");
        String hero = body.substring(heroStart, heroEnd);

        Assert.assertTrue(hero.contains("href=\"/automate\">Go to Automate</a>"));
        Assert.assertFalse(hero.contains("href=\"/upload\">Go to Upload</a>"));
        Assert.assertFalse(hero.contains("Go to Upload"));
    }

    @Test
    public void guide_section9_hasAutomateLink() throws Exception {
        String body = fetchGuideBody();

        int sectionStart = body.indexOf("data-guide-section=\"ai\"");
        Assert.assertTrue(sectionStart >= 0, "§9 section missing");
        int sectionEnd = body.indexOf("</section>", sectionStart);
        Assert.assertTrue(sectionEnd > sectionStart, "§9 section unclosed");
        String section9 = body.substring(sectionStart, sectionEnd);

        Assert.assertTrue(section9.contains("href=\"/automate\">Ready — open Automate</a>"));
    }

    @Test
    public void guide_hasManualTcTemplateDownload() throws Exception {
        String body = fetchGuideBody();
        Assert.assertTrue(body.contains("keel-manual-tcs-template.csv"), "TC template download link missing");
    }
}
