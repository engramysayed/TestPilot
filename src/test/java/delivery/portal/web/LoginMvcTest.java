package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:loginmvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-loginmvc",
        "delivery.work-dir=./target/test-delivery-work-loginmvc"
})
@AutoConfigureMockMvc
public class LoginMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void login_usesMockLoginpageLayout() throws Exception {
        String body = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("class=\"loginpage\""), "login must use loginpage grid");
        Assert.assertTrue(body.contains("class=\"loginbrand\""), "login must include brand panel");
        Assert.assertTrue(body.contains("From test intent"), "login must show mock tagline");
        Assert.assertTrue(body.contains("WEB AUTOMATION / ANDROID NEXT"));
        Assert.assertTrue(body.contains("Email address"));
        Assert.assertFalse(body.contains("login-split"), "old login-split layout removed");
    }

    @Test
    public void requestAccess_matchesLoginChrome() throws Exception {
        String body = mockMvc.perform(get("/request-access"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("class=\"loginpage\""));
        Assert.assertTrue(body.contains("class=\"loginbrand\""));
        Assert.assertTrue(body.contains("Request access"));
    }

    @Test
    public void invite_matchesLoginChrome() throws Exception {
        String body = mockMvc.perform(get("/invite/not-a-real-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("class=\"loginpage\""));
        Assert.assertTrue(body.contains("class=\"loginbrand\""));
        Assert.assertTrue(body.contains("Accept invite"));
        Assert.assertTrue(body.contains("keel-reskin.css"));
    }
}
