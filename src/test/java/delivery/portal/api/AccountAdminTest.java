package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalacct;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.mail-enabled=false",
        "delivery.store-root=./target/test-delivery-store-acct",
        "delivery.work-dir=./target/test-delivery-work-acct"
})
@AutoConfigureMockMvc
public class AccountAdminTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @Test
    public void changePassword_thenLoginWithNewPassword() throws Exception {
        mockMvc.perform(post("/api/account/password")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"ChangeMeAdmin1!\",\"newPassword\":\"NewAdminPass9!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/account")
                        .with(httpBasic("admin@testpilot.local", "NewAdminPass9!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@testpilot.local"));

        // restore for other tests in suite sharing context — re-encode original
        PortalUser admin = users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow();
        admin.setPasswordHash(encoder.encode("ChangeMeAdmin1!"));
        users.save(admin);
    }

    @Test
    public void adminCanDeleteOtherUser() throws Exception {
        PortalUser u = new PortalUser();
        u.setEmail("deleteme-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("DeleteMePass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        users.save(u);

        mockMvc.perform(delete("/api/admin/users/" + u.getId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        Assert.assertFalse(users.findById(u.getId()).isPresent());
    }

    @Test
    public void inviteWithoutMail_stillCreatesLink() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/admin/invites")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mailess-" + System.nanoTime() + "@example.com\",\"sendEmail\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        JSONObject json = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertFalse(json.getBoolean("emailSent"));
        Assert.assertTrue(json.getString("acceptUrl").contains("/invite/"));
    }
}
