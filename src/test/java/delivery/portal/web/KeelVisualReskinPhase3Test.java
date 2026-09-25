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
        "spring.datasource.url=jdbc:h2:mem:keelreskinp3;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-keelreskinp3",
        "delivery.work-dir=./target/test-delivery-work-keelreskinp3"
})
@AutoConfigureMockMvc
public class KeelVisualReskinPhase3Test extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void overview_matchesKeelMockStructure() throws Exception {
        String body = mockMvc.perform(get("/dashboard").with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("class=\"heading\""));
        Assert.assertTrue(body.contains("Your quality workspace"));
        Assert.assertTrue(body.contains("grid--overview"));
        Assert.assertTrue(body.contains("panel--keel"));
        Assert.assertTrue(body.contains("panelhead"));
        Assert.assertTrue(body.contains("View all runs"));
        Assert.assertTrue(body.contains("attention-item"), "attention list must use attention-item markup");
        Assert.assertTrue(body.contains("attention-mark"), "attention list must use attention-mark markup");
    }
}
