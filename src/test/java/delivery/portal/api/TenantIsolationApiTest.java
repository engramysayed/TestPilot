package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:tenantiso;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-tenantiso",
        "delivery.work-dir=./target/test-delivery-work-tenantiso"
})
@AutoConfigureMockMvc
public class TenantIsolationApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void secondCustomer() {
        if (!users.existsByEmailIgnoreCase("customer-b@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("customer-b@testpilot.local");
            u.setPasswordHash(encoder.encode("CustomerBee1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    @Test
    public void persistedTenantIsOpaqueAndArtifactRoutesRejectCrossTenant() throws Exception {
        String aliceId = createProject("admin@testpilot.local", "ChangeMeAdmin1!", "Alice Shop");
        String bobId = createProject("customer-b@testpilot.local", "CustomerBee1!", "Bob Shop");

        var alice = store.getOwnedProject(aliceId, adminUserId()).orElseThrow();
        var bob = store.getOwnedProject(bobId, bobUserId()).orElseThrow();
        Assert.assertTrue(alice.getTenantId().matches("ws_[a-f0-9]{32}"));
        Assert.assertTrue(bob.getTenantId().matches("ws_[a-f0-9]{32}"));
        Assert.assertNotEquals(alice.getTenantId(), bob.getTenantId());
        Assert.assertTrue(store.getOwnedProject(aliceId, bobUserId()).isEmpty());
        Assert.assertTrue(store.getOwnedProject(bobId, adminUserId()).isEmpty());

        Path aliceRoot = store.projectDiskRoot(aliceId);
        Path bobRoot = store.projectDiskRoot(bobId);
        Assert.assertNotEquals(aliceRoot, bobRoot);
        Assert.assertTrue(aliceRoot.toString().contains(alice.getTenantId()));
        Assert.assertTrue(bobRoot.toString().contains(bob.getTenantId()));
        Files.createDirectories(aliceRoot.resolve("versions"));
        Files.createDirectories(bobRoot.resolve("versions"));
        Files.writeString(aliceRoot.resolve("versions/v1.zip"), "ALICE_SENTINEL");
        Files.writeString(bobRoot.resolve("versions/v1.zip"), "BOB_SENTINEL");

        mockMvc.perform(get("/api/projects/" + aliceId + "/artifacts")
                        .with(httpBasic("customer-b@testpilot.local", "CustomerBee1!")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + bobId + "/artifacts")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());

        String aliceTree = mockMvc.perform(get("/api/projects/" + aliceId + "/artifacts")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(aliceTree.contains("versions/v1.zip"));
        Assert.assertFalse(aliceTree.contains("BOB_SENTINEL"));
        Assert.assertEquals(Files.readString(aliceRoot.resolve("versions/v1.zip")), "ALICE_SENTINEL");
        Assert.assertEquals(Files.readString(bobRoot.resolve("versions/v1.zip")), "BOB_SENTINEL");
    }

    private String createProject(String email, String password, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(email, password))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"baseUrl\":\"https://same.example.com/shop\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private Long adminUserId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private Long bobUserId() {
        return users.findByEmailIgnoreCase("customer-b@testpilot.local").orElseThrow().getId();
    }
}
