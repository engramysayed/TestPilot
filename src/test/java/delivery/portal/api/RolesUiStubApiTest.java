package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.RolesService;
import delivery.portal.roles.SystemRoleNames;
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
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roles UI / Final Roles approval shape — approve requires roleId in one call;
 * ApiError includes code (+ error) ROLE_REQUIRED; credits stay separate.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:rolesuiapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.sidebar.ia-enabled=true",
        "delivery.roles.ui-enabled=true",
        "delivery.store-root=./target/test-delivery-store-rolesuiapi",
        "delivery.work-dir=./target/test-delivery-work-rolesuiapi"
})
@AutoConfigureMockMvc
public class RolesUiStubApiTest extends AbstractTestNGSpringContextTests {

    private static final String USER = "admin@testpilot.local";
    private static final String PASS = "ChangeMeAdmin1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RolesService rolesService;

    private Long ensurePendingUser() {
        String email = "roles-stub-pending@testpilot.local";
        return users.findByEmailIgnoreCase(email).map(PortalUser::getId).orElseGet(() -> {
            PortalUser u = new PortalUser();
            u.setEmail(email);
            u.setPasswordHash(encoder.encode("PendingPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            u.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            u.setRoleId(null);
            return users.save(u).getId();
        });
    }

    private String memberRoleId() {
        return rolesService.findByName(SystemRoleNames.MEMBER).orElseThrow().getRoleId();
    }

    @Test
    public void rolesCrud_andPermissionCatalog() throws Exception {
        MvcResult cat = mockMvc.perform(get("/api/admin/permissions")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk()).andReturn();
        JSONArray perms = new JSONObject(cat.getResponse().getContentAsString()).getJSONArray("permissions");
        Assert.assertTrue(perms.length() >= 4);

        MvcResult created = mockMvc.perform(post("/api/admin/roles")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"QA lead\",\"description\":\"custom\",\"permissions\":[\"feature.generate\",\"feature.execute\"]}"))
                .andExpect(status().isCreated()).andReturn();
        JSONObject role = new JSONObject(created.getResponse().getContentAsString());
        Assert.assertTrue(role.getString("roleId").startsWith("role_"));
        Assert.assertFalse(role.getBoolean("system"));
    }

    @Test
    public void approve_requiresRoleId_singleCall() throws Exception {
        // Missing roleId → ROLE_REQUIRED with code (even before user resolution).
        mockMvc.perform(post("/api/admin/users/42/approval")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    JSONObject b = new JSONObject(result.getResponse().getContentAsString());
                    Assert.assertEquals(b.getString("code"), "ROLE_REQUIRED");
                    Assert.assertEquals(b.getString("error"), "ROLE_REQUIRED");
                });

        Long pendingId = ensurePendingUser();
        PortalUser reset = users.findById(pendingId).orElseThrow();
        reset.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        reset.setRoleId(null);
        users.save(reset);

        String roleId = memberRoleId();
        MvcResult ok = mockMvc.perform(post("/api/admin/users/" + pendingId + "/approval")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"roleId\":\"" + roleId + "\",\"note\":\"ok\"}"))
                .andExpect(status().isOk()).andReturn();
        JSONObject body = new JSONObject(ok.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("approvalStatus"), "APPROVED");
        Assert.assertEquals(body.getString("roleId"), roleId);
        Assert.assertTrue(body.getString("message").toLowerCase().contains("credit")
                || body.getString("message").toLowerCase().contains("separately"));
    }
}
