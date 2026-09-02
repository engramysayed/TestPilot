package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.AccessRequest;
import delivery.portal.persistence.AccessRequestRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalaccess;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.mail-enabled=false",
        "delivery.store-root=./target/test-delivery-store-access",
        "delivery.work-dir=./target/test-delivery-work-access"
})
@AutoConfigureMockMvc
public class AccessRequestApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccessRequestRepository requests;

    @Test
    public void publicCanSubmitAccessRequest() throws Exception {
        String email = "ask-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/access-requests")
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));

        Assert.assertTrue(requests.existsByEmailIgnoreCaseAndStatus(email, AccessRequest.Status.PENDING));
    }

    @Test
    public void adminCanApproveAccessRequest() throws Exception {
        String email = "approve-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/access-requests")
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated());

        AccessRequest req = requests.findFirstByEmailIgnoreCaseAndStatus(email, AccessRequest.Status.PENDING)
                .orElseThrow();

        MvcResult approved = mockMvc.perform(post("/api/admin/access-requests/" + req.getId() + "/approve")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sendEmail\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptUrl").exists())
                .andReturn();

        JSONObject body = new JSONObject(approved.getResponse().getContentAsString());
        Assert.assertTrue(body.getString("acceptUrl").contains("/invite/"));
        Assert.assertEquals(
                requests.findById(req.getId()).orElseThrow().getStatus(),
                AccessRequest.Status.APPROVED);

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAccessRequestCount").exists());
    }
}
