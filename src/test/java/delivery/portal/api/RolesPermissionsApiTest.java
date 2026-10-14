package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.PermissionCodes;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:rolesapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-rolesapi",
        "delivery.work-dir=./target/test-delivery-work-rolesapi"
})
@AutoConfigureMockMvc
public class RolesPermissionsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RolesService rolesService;

    @BeforeMethod
    public void seedUsers() {
        rolesService.ensureSystemRolesSeeded();
        ensureUser("member@testpilot.local", "MemberPass1!", PortalUser.Role.USER,
                ApprovalStatus.APPROVED, SystemRoleNames.MEMBER);
        ensureUser("pending@testpilot.local", "PendingPass1!", PortalUser.Role.USER,
                ApprovalStatus.PENDING_APPROVAL, null);
        ensureUser("noperm@testpilot.local", "NoPermPass1!", PortalUser.Role.USER,
                ApprovalStatus.APPROVED, null);
    }

    private void ensureUser(String email, String password, PortalUser.Role legacy,
                            ApprovalStatus approval, String systemRoleName) {
        PortalUser u = users.findByEmailIgnoreCase(email).orElseGet(PortalUser::new);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(legacy);
        u.setEnabled(true);
        u.setApprovalStatus(approval);
        if (systemRoleName != null) {
            rolesService.findByName(systemRoleName).ifPresent(r -> u.setRoleId(r.getRoleId()));
        } else if ("noperm@testpilot.local".equals(email)) {
            String name = "NoFeatureRole";
            String rid = rolesService.findByName(name)
                    .map(r -> r.getRoleId())
                    .orElseGet(() -> (String) rolesService.createRole(name, "no features",
                            java.util.List.of(PermissionCodes.PROJECT_READ, PermissionCodes.CREDITS_VIEW_SELF))
                            .get("roleId"));
            u.setRoleId(rid);
        } else {
            u.setRoleId(null);
        }
        if (approval == ApprovalStatus.APPROVED) {
            u.setApprovedAt(java.time.Instant.now());
        }
        users.save(u);
    }

    private String ownerRoleId() {
        return rolesService.findByName(SystemRoleNames.OWNER).orElseThrow().getRoleId();
    }

    private String memberRoleId() {
        return rolesService.findByName(SystemRoleNames.MEMBER).orElseThrow().getRoleId();
    }

    private String adminAuth() {
        return "admin@testpilot.local";
    }

    @Test
    public void ownerCanListRolesAndPermissionsCatalog() throws Exception {
        mockMvc.perform(get("/api/admin/roles")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray());

        mockMvc.perform(get("/api/admin/permissions")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    public void memberCannotManageRoles() throws Exception {
        mockMvc.perform(get("/api/admin/roles")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void systemRolesNotDeletable() throws Exception {
        String roleId = memberRoleId();
        MvcResult r = mockMvc.perform(delete("/api/admin/roles/" + roleId)
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden())
                .andReturn();
        JSONObject body = new JSONObject(r.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("error"), "FORBIDDEN");
    }

    @Test
    public void approveWithoutRoleIdReturns400RoleRequired() throws Exception {
        Long pendingId = users.findByEmailIgnoreCase("pending@testpilot.local").orElseThrow().getId();
        PortalUser reset = users.findById(pendingId).orElseThrow();
        reset.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        reset.setRoleId(null);
        users.save(reset);
        MvcResult r = mockMvc.perform(post("/api/admin/users/" + pendingId + "/approval")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JSONObject body = new JSONObject(r.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("error"), "ROLE_REQUIRED");
        PortalUser still = users.findById(pendingId).orElseThrow();
        Assert.assertEquals(still.getApprovalStatus(), ApprovalStatus.PENDING_APPROVAL);
        // Must not silently default-assign MEMBER
        Assert.assertTrue(still.getRoleId() == null || still.getRoleId().isBlank());
    }

    @Test
    public void approveWithRoleIdAssignsRoleNoDefaultMember() throws Exception {
        Long pendingId = users.findByEmailIgnoreCase("pending@testpilot.local").orElseThrow().getId();
        // Reset pending
        PortalUser p = users.findById(pendingId).orElseThrow();
        p.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        p.setRoleId(null);
        users.save(p);

        String adminRoleId = rolesService.findByName(SystemRoleNames.ADMIN).orElseThrow().getRoleId();
        MvcResult r = mockMvc.perform(post("/api/admin/users/" + pendingId + "/approval")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"roleId\":\"" + adminRoleId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = new JSONObject(r.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("approvalStatus"), "APPROVED");
        Assert.assertEquals(body.getString("roleId"), adminRoleId);
        Assert.assertNotEquals(body.getString("roleId"), memberRoleId());

        PortalUser updated = users.findById(pendingId).orElseThrow();
        Assert.assertEquals(updated.getApprovalStatus(), ApprovalStatus.APPROVED);
        Assert.assertEquals(updated.getRoleId(), adminRoleId);
        Assert.assertNotEquals(updated.getRoleId(), memberRoleId());
    }

    @Test
    public void mePermissionsIncludesApprovalStatus() throws Exception {
        mockMvc.perform(get("/api/me/permissions")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.roleId").isString());
    }

    @Test
    public void adminUsersListIncludesApprovalStatus() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = new JSONObject(r.getResponse().getContentAsString());
        JSONArray arr = body.getJSONArray("users");
        Assert.assertTrue(arr.length() > 0);
        Assert.assertTrue(arr.getJSONObject(0).has("approvalStatus"));
        Assert.assertTrue(arr.getJSONObject(0).has("roleId"));
    }

    @Test
    public void pendingUserCannotStartAccountJob() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/account/jobs")
                        .with(httpBasic("pending@testpilot.local", "PendingPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GENERATE\"}"))
                .andExpect(status().isForbidden())
                .andReturn();
        JSONObject body = new JSONObject(r.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("error"), "ACCOUNT_NOT_APPROVED");
    }

    @Test
    public void approvedWithoutFeaturePermissionBlockedOnAccountJob() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/account/jobs")
                        .with(httpBasic("noperm@testpilot.local", "NoPermPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXECUTE\"}"))
                .andExpect(status().isForbidden())
                .andReturn();
        JSONObject body = new JSONObject(r.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("error"), "ROLE_REQUIRED");
    }

    @Test
    public void memberWithPermissionCanStartAccountScopedJob() throws Exception {
        mockMvc.perform(post("/api/account/jobs")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GENERATE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountScoped").value(true))
                .andExpect(jsonPath("$.jobId").isString());
    }

    @Test
    public void createCustomRoleAndOwnerManage() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/roles")
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Analyst\",\"permissions\":[\"feature.generate\",\"project.read\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String roleId = new JSONObject(created.getResponse().getContentAsString()).getString("roleId");
        mockMvc.perform(patch("/api/admin/roles/" + roleId)
                        .with(httpBasic(adminAuth(), "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"job.execute\"]}"))
                .andExpect(status().isOk());
        // job.execute alias normalized into feature.execute on replace for non-system — patch merges for system only;
        // for custom, replace — so only feature.execute after this patch
    }
}
