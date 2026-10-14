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
        "spring.datasource.url=jdbc:h2:mem:rolesuimvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.sidebar.ia-enabled=true",
        "delivery.roles.ui-enabled=true",
        "delivery.store-root=./target/test-delivery-store-rolesuimvc",
        "delivery.work-dir=./target/test-delivery-work-rolesuimvc"
})
@AutoConfigureMockMvc
public class RolesUiMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void adminRoles_whenFlagOn_returns200WithRolesUi() throws Exception {
        String body = mockMvc.perform(get("/admin/roles")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("Roles &amp; permissions") || body.contains("Roles & permissions"));
        Assert.assertTrue(body.contains("perm-checklist") || body.contains("keel-perm-grid"));
        Assert.assertTrue(body.contains("/api/admin/roles"));
        Assert.assertTrue(body.contains("/api/admin/permissions"));
        Assert.assertTrue(body.contains("data-roles-ui"), "data-roles-ui attr missing");
        Assert.assertTrue(body.contains("No roles yet"), "empty roles state copy missing");
        Assert.assertTrue(body.contains("getAttribute('data-roles-ui')"),
                "page JS must honor data-roles-ui (client belt)");
    }
}
