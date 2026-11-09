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
        "spring.datasource.url=jdbc:h2:mem:sidebariamvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.sidebar.ia-enabled=true",
        "delivery.roles.ui-enabled=true",
        "delivery.credits.enabled=true",
        "delivery.store-root=./target/test-delivery-store-sidebariamvc",
        "delivery.work-dir=./target/test-delivery-work-sidebariamvc"
})
@AutoConfigureMockMvc
public class SidebarIaMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void dashboard_hasCreditsChipAndFeaturesSection() throws Exception {
        String body = mockMvc.perform(get("/dashboard")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("keel-credits-chip"), "credits chip missing");
        Assert.assertTrue(body.contains("/api/account/credits") || body.contains("keel-sidebar-ia.js"), "credits JS missing");
        Assert.assertTrue(body.contains("data-feature-perm=\"feature.generate\""), "feature.generate gate missing");
        Assert.assertTrue(body.contains("data-feature-perm=\"feature.execute\""));
        Assert.assertTrue(body.contains("data-feature-perm=\"feature.automate\""));
        Assert.assertTrue(body.contains("data-feature-perm=\"feature.hunt\""));
        Assert.assertTrue(body.contains("data-features-label") || body.contains(">Features<"), "Features label missing");
        Assert.assertTrue(body.contains("keel-pending-banner"), "pending banner missing");
        Assert.assertTrue(body.contains("keel-sidebar-roles.css"), "pack CSS missing");
    }

    @Test
    public void generate_hasAccountScopedProjectHint() throws Exception {
        String body = mockMvc.perform(get("/generate")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("account-scoped-picker-hint") || body.contains("Choose a project"),
                "project picker hint missing");
        Assert.assertTrue(body.contains("Write test cases") || body.contains("Generate"));
    }
}
