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
        "spring.datasource.url=jdbc:h2:mem:accountcreditson;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.credits.enabled=true",
        "delivery.store-root=./target/test-delivery-store-accountcreditson",
        "delivery.work-dir=./target/test-delivery-work-accountcreditson"
})
@AutoConfigureMockMvc
public class AccountCreditsMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void account_whenCreditsEnabled_showsCreditsPanel() throws Exception {
        String body = mockMvc.perform(get("/account")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("id=\"account-credits-panel\"") || body.contains("account-credits-panel"),
                "credits panel missing");
        Assert.assertTrue(body.contains("account-credits-balance") || body.contains("credits"),
                "balance line missing");
        Assert.assertTrue(body.contains("No ledger entries") || body.contains("header chip"),
                "empty ledger / help copy missing");
    }
}
