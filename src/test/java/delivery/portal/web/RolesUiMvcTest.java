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
    public void adminRoles_pageHasChecklist() throws Exception {
        String body = mockMvc.perform(get("/admin/roles")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("Roles &amp; permissions") || body.contains("Roles & permissions"));
        Assert.assertTrue(body.contains("perm-checklist") || body.contains("keel-perm-grid"));
        Assert.assertTrue(body.contains("/api/admin/roles"));
        Assert.assertTrue(body.contains("/api/admin/permissions"));
    }

    @Test
    public void adminUsers_approveModalRequiresRole_singlePost() throws Exception {
        String body = mockMvc.perform(get("/admin/users")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("keel-approve-modal"), "approve modal missing");
        Assert.assertTrue(body.contains("approve-role"), "role picker missing");
        Assert.assertTrue(body.contains("Choose a role"), "must require explicit role");
        Assert.assertTrue(body.contains("Credits stay at zero")
                        || body.contains("Set credits separately")
                        || body.contains("nothing was added automatically"),
                "must not promise starter credits");
        Assert.assertTrue(body.contains("/approval"), "approval endpoint missing");
        Assert.assertTrue(body.contains("roleId: roleId") || body.contains("roleId:roleId"),
                "approve body must include roleId in single POST");
        int idx = body.indexOf("approve-confirm");
        Assert.assertTrue(idx >= 0);
        String handler = body.substring(idx, Math.min(body.length(), idx + 1500));
        Assert.assertTrue(handler.contains("/approval"));
        // After posting approval, handler should not also POST .../role
        int approvalAt = handler.indexOf("/approval");
        String afterApproval = handler.substring(approvalAt);
        Assert.assertFalse(afterApproval.contains("/role'"),
                "approve handler must not call /role after /approval");
        Assert.assertFalse(afterApproval.contains("/role\""),
                "approve handler must not call /role after /approval");
    }
}
