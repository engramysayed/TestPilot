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
        "spring.datasource.url=jdbc:h2:mem:hunttriagemvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.hunt.triage.enabled=true",
        "delivery.store-root=./target/test-delivery-store-hunttriagemvc",
        "delivery.work-dir=./target/test-delivery-work-hunttriagemvc"
})
@AutoConfigureMockMvc
public class HuntTriageMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void bugHunter_showsTriageQueueWhenFlagOn() throws Exception {
        String body = mockMvc.perform(get("/bug-hunter")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"hunt-triage-panel\""), "triage panel missing");
        Assert.assertTrue(body.contains("Review findings"), "plain-language triage heading missing");
        Assert.assertTrue(body.contains("Mark confirmed"), "confirm action missing");
        Assert.assertTrue(body.contains("Dismiss"), "dismiss action missing");
        Assert.assertTrue(body.contains("Needs more info"), "needs-more-info action missing");
        Assert.assertTrue(body.contains("Promote to library"), "explicit promote control missing");
        Assert.assertTrue(body.contains("/hunt/") && body.contains("/bugs"), "job-scoped bugs API wiring missing");
        Assert.assertTrue(body.contains("/summary"), "summary endpoint wiring missing");
        Assert.assertTrue(body.contains("/promote"), "promote endpoint wiring missing");
        Assert.assertTrue(body.contains("CONFIRMED") || body.contains("confirm"),
                "CONFIRMED status mapping missing");
        Assert.assertTrue(body.contains("does not add a test case")
                        || body.contains("Nothing was added to the library"),
                "must explain CONFIRMED does not promote");
        Assert.assertFalse(body.contains("Backend pending"), "must not claim Backend still pending");
        Assert.assertFalse(body.contains("HuntTriageStub"), "stub controller must not be referenced");
        Assert.assertTrue(body.contains("TC draft created/linked") || body.contains("tcDraftId"),
                "promote messaging must match real API (tcDraftId / TC draft)");
        Assert.assertTrue(body.contains("keel-portal-enhancements.css"), "enhancement CSS missing");
    }
}
