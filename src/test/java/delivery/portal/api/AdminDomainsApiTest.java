package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portaldomains;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.store-root=./target/test-delivery-store-domains",
        "delivery.work-dir=./target/test-delivery-work-domains"
})
@AutoConfigureMockMvc
public class AdminDomainsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @Test
    public void nonAdmin_getsForbidden() throws Exception {
        String email = "user-domains-" + System.nanoTime() + "@testpilot.local";
        PortalUser u = new PortalUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("UserPass123!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        users.save(u);

        mockMvc.perform(get("/api/admin/domains")
                        .with(httpBasic(email, "UserPass123!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void admin_canListDomains() throws Exception {
        Path root = Path.of("./target/test-delivery-store-domains");
        Files.createDirectories(root.resolve("demo-com/prj_a"));
        Files.writeString(root.resolve("demo-com/prj_a/project.json"), "{\"version\":1}");

        mockMvc.perform(get("/api/admin/domains")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());
    }

    @Test
    public void unauthenticated_img_isPublic() throws Exception {
        mockMvc.perform(get("/img/keel-logo.svg"))
                .andExpect(status().isOk());
    }

    @Test
    public void deleteRejectsMismatch() throws Exception {
        mockMvc.perform(delete("/api/admin/domains/demo-com")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType("application/json")
                        .content("{\"confirm\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
    }
}
