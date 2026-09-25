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
        "spring.datasource.url=jdbc:h2:mem:keelreskinp1;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-keelreskinp1",
        "delivery.work-dir=./target/test-delivery-work-keelreskinp1"
})
@AutoConfigureMockMvc
public class KeelVisualReskinPhase1Test extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shell_hasKeelPreviewSidebarAndTopbar() throws Exception {
        String body = mockMvc.perform(get("/dashboard").with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("workspace-pill"), "sidebar must show workspace pill");
        Assert.assertTrue(body.contains("Designed for clear evidence"), "sidebar must show sidehelp");
        Assert.assertTrue(body.contains("class=\"topbar\""), "page must include topbar");
        Assert.assertTrue(body.contains("class=\"crumb\""), "topbar must include breadcrumb");
        Assert.assertTrue(body.contains("class=\"avatar"), "topbar must include avatar");
        Assert.assertFalse(body.contains(">Dashboard<"), "nav must not say Dashboard");
        Assert.assertTrue(body.contains(">Overview<"), "nav must say Overview");
        Assert.assertTrue(body.contains("A clearer view of every run"), "overview hero headline must match mock");
    }
}
