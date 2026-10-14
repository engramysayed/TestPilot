package delivery.portal.api;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
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

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m306-casereq;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m306-casereq",
        "delivery.work-dir=./target/test-delivery-work-mobile-m306-casereq",
        "delivery.mobile.enabled=true",
        "delivery.mobile.live-tests=false"
})
@AutoConfigureMockMvc
public class MobileCaseRequirementsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired MockMvc mockMvc;
    @Autowired PortalStore store;
    @Autowired PortalUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeMethod
    public void ensureMember() {
        if (!users.existsByEmailIgnoreCase("member@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("member@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private long memberId() {
        return users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId();
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M306 CaseReq\",\"baseUrl\":\"https://ex.test/\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void caseRequirementsRoundTripAndNegativeLoginReject() throws Exception {
        String projectId = createProject();

        MvcResult put = mockMvc.perform(put("/api/projects/" + projectId + "/mobile/cases/TC-LEAF/requirements")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authMode":"FRESH_LOGIN","callBefore":["TC-SETUP"],
                                 "authProfileRevisionId":"apr_abc","startStateTcId":"TC-SS",
                                 "negativeLogin":false}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = new JSONObject(put.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("tcId"), "TC-LEAF");
        Assert.assertEquals(body.getString("authMode"), "FRESH_LOGIN");
        Assert.assertEquals(body.getJSONArray("callBefore").getString(0), "TC-SETUP");
        Assert.assertEquals(body.getString("authProfileRevisionId"), "apr_abc");
        Assert.assertEquals(body.getString("startStateTcId"), "TC-SS");
        Assert.assertFalse(body.getBoolean("negativeLogin"));

        MvcResult get = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/cases/TC-LEAF/requirements")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject got = new JSONObject(get.getResponse().getContentAsString());
        Assert.assertEquals(got.getString("authMode"), "FRESH_LOGIN");
        Assert.assertEquals(got.getJSONArray("callBefore").length(), 1);

        // seed a negative-login case, then reject using it as setup
        mockMvc.perform(put("/api/projects/" + projectId + "/mobile/cases/TC-BAD-LOGIN/requirements")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"negativeLogin\":true,\"callBefore\":[]}"))
                .andExpect(status().isOk());

        MvcResult rejected = mockMvc.perform(put("/api/projects/" + projectId + "/mobile/cases/TC-OTHER/requirements")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callBefore\":[\"TC-BAD-LOGIN\"],\"negativeLogin\":false}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JSONObject err = new JSONObject(rejected.getResponse().getContentAsString());
        Assert.assertEquals(err.getString("error"), "NEGATIVE_LOGIN_SETUP");

        MvcResult list = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/cases/requirements")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        Assert.assertTrue(new JSONObject(list.getResponse().getContentAsString()).getJSONArray("cases").length() >= 2);
    }

    @Test
    public void memberCanReadButCannotPut() throws Exception {
        String projectId = createProject();
        var project = store.getOwnedProject(projectId, adminId()).orElseThrow();
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-mobile-m306-casereq"))
                .addMember(TenantId.parse(project.getTenantId()), memberId(), WorkspaceRole.MEMBER);

        mockMvc.perform(put("/api/projects/" + projectId + "/mobile/cases/TC-1/requirements")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authMode\":\"LOGGED_OUT\",\"callBefore\":[]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/mobile/cases/TC-1/requirements")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/projects/" + projectId + "/mobile/cases/TC-1/requirements")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"callBefore\":[\"X\"]}"))
                .andExpect(status().isForbidden());
    }
}
