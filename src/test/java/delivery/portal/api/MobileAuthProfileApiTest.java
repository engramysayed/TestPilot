package delivery.portal.api;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONArray;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m306-auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m306-auth",
        "delivery.work-dir=./target/test-delivery-work-mobile-m306-auth",
        "delivery.mobile.enabled=true",
        "delivery.mobile.live-tests=false"
})
@AutoConfigureMockMvc
public class MobileAuthProfileApiTest extends AbstractTestNGSpringContextTests {

    private static final String SECRET_CANARY = "SECRET_CANARY_OTP_999888";

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
                        .content("{\"name\":\"M306 Auth\",\"baseUrl\":\"https://ex.test/\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private static String createBody() {
        return """
                {"loginTcId":"TC-LOGIN","defaultMode":"ENSURE_LOGGED_IN",
                 "sessionPredicates":["EXISTS:home"],"signedOutPredicates":["EXISTS:login"],
                 "logoutTcId":"TC-LOGOUT","setupTcId":"",
                 "account":{"accountId":"acct-1","displayLabel":"Primary"},
                 "inputDeclarations":[
                   {"key":"auth.password","kind":"PASSWORD","requiredPerRun":false},
                   {"key":"auth.otp","kind":"OTP","requiredPerRun":true}
                 ],
                 "protectedCredentialRefs":[{"credentialId":"cred-1","logicalKey":"auth.password"}]
                }
                """;
    }

    @Test
    public void createEditRevision_conflictAndNoSecretCanary() throws Exception {
        String projectId = createProject();

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/mobile/auth-profiles")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn();
        String createdJson = created.getResponse().getContentAsString();
        Assert.assertFalse(createdJson.contains(SECRET_CANARY), createdJson);
        Assert.assertFalse(createdJson.contains("\"value\""), createdJson);
        JSONObject rev1 = new JSONObject(createdJson);
        String profileId = rev1.getString("profileId");
        String revisionId = rev1.getString("revisionId");
        Assert.assertEquals(rev1.getString("defaultMode"), "ENSURE_LOGGED_IN");
        Assert.assertEquals(rev1.getString("loginTcId"), "TC-LOGIN");
        Assert.assertTrue(rev1.has("inputDeclarations"));
        Assert.assertFalse(rev1.toString().contains(SECRET_CANARY));

        MvcResult list = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/auth-profiles")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject listBody = new JSONObject(list.getResponse().getContentAsString());
        Assert.assertTrue(listBody.getBoolean("canEdit"));
        Assert.assertEquals(listBody.getJSONArray("profiles").length(), 1);
        Assert.assertEquals(listBody.getJSONArray("profiles").getJSONObject(0).getString("profileId"), profileId);

        MvcResult get = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/auth-profiles/" + profileId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject getBody = new JSONObject(get.getResponse().getContentAsString());
        Assert.assertEquals(getBody.getString("activeRevisionId"), revisionId);
        Assert.assertEquals(getBody.getJSONObject("revision").getString("revisionId"), revisionId);

        String editBody = """
                {"expectedActiveRevisionId":"%s","loginTcId":"TC-LOGIN-2","defaultMode":"FRESH_LOGIN",
                 "sessionPredicates":["EXISTS:home2"],"signedOutPredicates":[],
                 "logoutTcId":"TC-LOGOUT","setupTcId":"",
                 "account":{"accountId":"acct-1","displayLabel":"Primary"},
                 "inputDeclarations":[{"key":"auth.otp","kind":"OTP","requiredPerRun":true}],
                 "protectedCredentialRefs":[]}
                """.formatted(revisionId);
        MvcResult edited = mockMvc.perform(post("/api/projects/" + projectId
                        + "/mobile/auth-profiles/" + profileId + "/revisions")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody))
                .andExpect(status().isCreated())
                .andReturn();
        JSONObject rev2 = new JSONObject(edited.getResponse().getContentAsString());
        Assert.assertNotEquals(rev2.getString("revisionId"), revisionId);
        Assert.assertEquals(rev2.getString("defaultMode"), "FRESH_LOGIN");
        Assert.assertEquals(rev2.getString("loginTcId"), "TC-LOGIN-2");
        Assert.assertFalse(edited.getResponse().getContentAsString().contains(SECRET_CANARY));

        // stale base → REVISION_CONFLICT
        MvcResult conflict = mockMvc.perform(post("/api/projects/" + projectId
                        + "/mobile/auth-profiles/" + profileId + "/revisions")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody))
                .andExpect(status().isConflict())
                .andReturn();
        JSONObject err = new JSONObject(conflict.getResponse().getContentAsString());
        Assert.assertEquals(err.getString("error"), "REVISION_CONFLICT");

        // immutable prior revision still readable
        mockMvc.perform(get("/api/projects/" + projectId + "/mobile/auth-profiles/" + profileId
                        + "/revisions/" + revisionId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        // reject raw secret values on create
        mockMvc.perform(post("/api/projects/" + projectId + "/mobile/auth-profiles")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginTcId\":\"TC-X\",\"password\":\"" + SECRET_CANARY + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void memberCanReadButCannotMutate() throws Exception {
        String projectId = createProject();
        var project = store.getOwnedProject(projectId, adminId()).orElseThrow();
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-mobile-m306-auth"))
                .addMember(TenantId.parse(project.getTenantId()), memberId(), WorkspaceRole.MEMBER);

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/mobile/auth-profiles")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andReturn();
        String profileId = new JSONObject(created.getResponse().getContentAsString()).getString("profileId");

        MvcResult list = mockMvc.perform(get("/api/projects/" + projectId + "/mobile/auth-profiles")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String listJson = list.getResponse().getContentAsString();
        Assert.assertTrue(listJson.contains("\"canEdit\":false") || listJson.contains("\"canEdit\": false"));
        Assert.assertFalse(listJson.contains(SECRET_CANARY));

        mockMvc.perform(get("/api/projects/" + projectId + "/mobile/auth-profiles/" + profileId)
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/" + projectId + "/mobile/auth-profiles")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/mobile/auth-profiles/" + profileId + "/revisions")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedActiveRevisionId\":\"x\",\"loginTcId\":\"TC\"}"))
                .andExpect(status().isForbidden());
    }
}
