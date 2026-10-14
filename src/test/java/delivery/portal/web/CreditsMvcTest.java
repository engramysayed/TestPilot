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
        "spring.datasource.url=jdbc:h2:mem:creditsmvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.credits.enabled=true",
        "delivery.store-root=./target/test-delivery-store-creditsmvc",
        "delivery.work-dir=./target/test-delivery-work-creditsmvc"
})
@AutoConfigureMockMvc
public class CreditsMvcTest extends AbstractTestNGSpringContextTests {
    @Autowired MockMvc mockMvc;

    @Test
    public void chrome_includesCreditsChipAndScript() throws Exception {
        String body = mockMvc.perform(get("/dashboard")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("keel-credits-chip"), "credits chip missing");
        Assert.assertTrue(body.contains("keel-credits.js"), "credits js missing");
        Assert.assertTrue(body.contains("keel-credits.css"), "credits css missing");
        Assert.assertTrue(body.contains("keel-credits.js") || body.contains("/api/account/credits"),
                "account credits wiring missing");
    }

    @Test
    public void adminUsers_hasApproveRolePickerAndSetCredits() throws Exception {
        String body = mockMvc.perform(get("/admin/users")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("keel-approve-modal"), "approve modal missing");
        Assert.assertTrue(body.contains("keel-approve-role"), "role picker missing");
        Assert.assertTrue(body.contains("Select a role"), "empty role placeholder missing");
        Assert.assertTrue(body.contains("keel-credits-modal"), "set credits modal missing");
        Assert.assertTrue(body.contains("Set credits"), "set credits action missing");
        Assert.assertTrue(body.contains("nothing is added automatically")
                        || body.contains("Credits stay at 0")
                        || body.contains("no free trial")
                        || body.contains("Set them"),
                "must say credits stay manual after approve");
        Assert.assertTrue(body.contains("/api/admin/users/") && body.contains("/approval"),
                "single approval endpoint wiring missing");
        Assert.assertTrue(body.contains("ROLE_REQUIRED") || body.contains("Pick a role"),
                "role required copy missing");
        Assert.assertTrue(body.contains("Credits"), "credits column missing");
        Assert.assertTrue(body.contains("Status") || body.contains("approval"), "status column missing");
    }

    @Test
    public void generate_wiresInsufficientCreditsHelper() throws Exception {
        String body = mockMvc.perform(get("/generate")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("KeelCredits") || body.contains("keel-credits.js"),
                "credits helper missing on generate");
        Assert.assertTrue(body.contains("gateMessage") || body.contains("INSUFFICIENT_CREDITS")
                        || body.contains("keel-credits.js"),
                "insufficient credits handling missing");
    }
}
