package delivery.portal.web;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalnavsearch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-portalnavsearch",
        "delivery.work-dir=./target/test-delivery-work-portalnavsearch"
})
@AutoConfigureMockMvc
public class PortalNavSearchMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void ensureNonAdmin() {
        if (!users.existsByEmailIgnoreCase("member@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("member@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    @Test
    public void search_findsWorkspacePages() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/portal/search")
                        .param("q", "runs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray hits = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertTrue(hits.length() > 0);
        Assert.assertTrue(hits.getJSONObject(0).getString("url").contains("/runs"));
    }

    @Test
    public void search_findsProjectByName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Commerce Checkout\",\"baseUrl\":\"https://shop.test\"}"))
                .andExpect(status().isCreated());

        MvcResult res = mockMvc.perform(get("/api/portal/search")
                        .param("q", "Commerce")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray hits = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertEquals(hits.getJSONObject(0).getString("kind"), "project");
    }

    @Test
    public void dashboard_includesGlobalSearch() throws Exception {
        String body = mockMvc.perform(get("/dashboard")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"globalSearch\""));
        Assert.assertTrue(body.contains("portal-nav.js"));
    }

    @Test
    public void search_adminSeesAdministrationAndDomains() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/portal/search")
                        .param("q", "admin")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String json = res.getResponse().getContentAsString();
        Assert.assertTrue(json.contains("/admin/users") || json.contains("Administration"),
                "admin must see Administration");
        MvcResult domains = mockMvc.perform(get("/api/portal/search")
                        .param("q", "domains")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String djson = domains.getResponse().getContentAsString();
        Assert.assertTrue(djson.contains("/admin/domains") || djson.contains("Domains"),
                "admin must see Domains");
    }

    @Test
    public void search_nonAdminDoesNotSeeAdministrationOrDomains() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/portal/search")
                        .param("q", "admin")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String json = res.getResponse().getContentAsString();
        Assert.assertFalse(json.contains("/admin/users"), "non-admin must not see /admin/users");
        Assert.assertFalse(json.contains("Administration"), "non-admin must not see Administration label");

        MvcResult domains = mockMvc.perform(get("/api/portal/search")
                        .param("q", "domains")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String djson = domains.getResponse().getContentAsString();
        Assert.assertFalse(djson.contains("/admin/domains"), "non-admin must not see /admin/domains");
    }

    @Test
    public void sidebar_jobChipLabelsHunt() throws Exception {
        String body = mockMvc.perform(get("/dashboard")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(body.contains("kind === 'HUNT'") || body.contains("kind === \"HUNT\""),
                "HUNT kindLabel branch missing");
        Assert.assertTrue(body.contains("Hunting…") || body.contains("Hunting"),
                "Hunting… label missing");
        Assert.assertTrue(body.contains("GENERATE_BATCH") && body.contains("EXECUTE")
                        && body.contains("GENERATE_COMPARE"),
                "existing kind labels must remain");
    }
}
