package delivery.portal.api;

import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.RolesService;
import delivery.portal.roles.SystemRoleNames;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.credits.UserCreditsRepository;
import delivery.portal.service.credits.CreditService;
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
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalcredits;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.mail-enabled=false",
        "delivery.credits.enabled=true",
        "delivery.credits.cost.generate=10",
        "delivery.credits.cost.execute=5",
        "delivery.credits.cost.automate=25",
        "delivery.credits.cost.hunt=15",
        "delivery.store-root=./target/test-delivery-store-credits",
        "delivery.work-dir=./target/test-delivery-work-credits"
})
@AutoConfigureMockMvc
public class CreditsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CreditService credits;
    @Autowired private UserCreditsRepository userCredits;
    @Autowired private RolesService rolesService;

    private PortalUser member;

    @BeforeMethod
    public void ensureMember() {
        member = users.findByEmailIgnoreCase("member-credits@testpilot.local").orElseGet(() -> {
            PortalUser u = new PortalUser();
            u.setEmail("member-credits@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            u.setApprovalStatus(ApprovalStatus.APPROVED);
            return users.save(u);
        });
        if (member.getApprovalStatus() != ApprovalStatus.APPROVED || member.getRoleId() == null || member.getRoleId().isBlank()) {
            member.setApprovalStatus(ApprovalStatus.APPROVED);
            member.setRoleId(memberRoleIdForTest());
            users.save(member);
        }
    }

    @Test
    public void memberForbiddenOnAdminCredits() throws Exception {
        mockMvc.perform(get("/api/admin/credits/costs")
                        .with(httpBasic("member-credits@testpilot.local", "MemberPass1!")))
                .andExpect(status().isForbidden());
    }

    @Test
    public void adminGrantAdjustLedgerAndIdempotent() throws Exception {
        String key = "grant-" + UUID.randomUUID();
        MvcResult res = mockMvc.perform(post("/api/admin/credits/" + member.getId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"GRANT_DELTA\",\"amount\":100,\"note\":\"wire starter\",\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(member.getId().intValue()))
                .andExpect(jsonPath("$.deltaApplied").value(100))
                .andExpect(jsonPath("$.balance").value(100))
                .andReturn();
        long ledgerId = new JSONObject(res.getResponse().getContentAsString()).getLong("ledgerEntryId");

        // idempotent replay
        mockMvc.perform(post("/api/admin/credits/" + member.getId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"GRANT_DELTA\",\"amount\":100,\"note\":\"wire starter\",\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerEntryId").value(ledgerId))
                .andExpect(jsonPath("$.balance").value(100));

        mockMvc.perform(get("/api/admin/credits/" + member.getId() + "/ledger")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(member.getId().intValue()))
                .andExpect(jsonPath("$.balance").value(100))
                .andExpect(jsonPath("$.entries").isArray());

        mockMvc.perform(get("/api/account/credits")
                        .with(httpBasic("member-credits@testpilot.local", "MemberPass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100))
                .andExpect(jsonPath("$.costs.generate").value(10))
                .andExpect(jsonPath("$.costs.automate").value(25))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray());
    }

    @Test
    public void setAbsoluteAndApproveDoesNotGrantCredits() throws Exception {
        PortalUser pending = new PortalUser();
        pending.setEmail("pending-" + System.nanoTime() + "@testpilot.local");
        pending.setPasswordHash(encoder.encode("PendingPass1!"));
        pending.setRole(PortalUser.Role.USER);
        pending.setEnabled(true);
        pending.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        users.save(pending);

        Assert.assertEquals(userCredits.findById(pending.getId()).map(c -> c.getBalance()).orElse(0L), 0L);

        mockMvc.perform(post("/api/admin/users/" + pending.getId() + "/approval")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"roleId\":\"" + memberRoleIdForTest() + "\",\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        // still zero credits — no trial grant
        Assert.assertEquals(credits.userCreditsDto(pending.getId()).get("balance"), 0L);

        String key = "set-" + UUID.randomUUID();
        mockMvc.perform(post("/api/admin/credits/" + pending.getId())
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SET_ABSOLUTE\",\"amount\":40,\"note\":\"after pay\",\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(40))
                .andExpect(jsonPath("$.deltaApplied").value(40));
    }

    @Test
    public void insufficientCreditsRefusesDebitIdempotentKey() throws Exception {
        PortalUser u = new PortalUser();
        u.setEmail("poor-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("PoorPass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        users.save(u);

        Path excel = Files.createTempFile("credits-exec", ".xlsx");
        delivery.portal.model.JobRecord job = new delivery.portal.model.JobRecord(
                "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "proj_x", u.getId(), "EXECUTE", excel, "https://example.com", "", "", false,
                delivery.portal.model.JobRecord.JobKind.EXECUTE);

        try {
            credits.tryDebitForJob(job);
            Assert.fail("expected InsufficientCreditsException");
        } catch (delivery.portal.service.credits.InsufficientCreditsException e) {
            Assert.assertEquals(e.required(), 5L);
            Assert.assertEquals(e.balance(), 0L);
            Assert.assertEquals(e.jobKind(), "EXECUTE");
        }
        // job must not have a ledger debit
        Assert.assertTrue(credits.usageForUser(u.getId(), null, null, null)
                .get("entries").toString().equals("[]")
                || ((java.util.List<?>) credits.usageForUser(u.getId(), null, null, null).get("entries")).isEmpty());
    }

    @Test
    public void pendingApprovalBlocksDebitDistinctFromCredits() throws Exception {
        PortalUser u = new PortalUser();
        u.setEmail("pendjob-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("PendPass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        users.save(u);
        // grant credits anyway — approval still blocks (distinct from INSUFFICIENT_CREDITS)
        credits.setCredits(u.getId(), "GRANT_DELTA", 100, "test pack", "pre-" + UUID.randomUUID(), null);

        delivery.portal.service.credits.AccountApprovalService approvals =
                applicationContext.getBean(delivery.portal.service.credits.AccountApprovalService.class);
        try {
            approvals.requireApprovedForJobs(u.getId());
            Assert.fail("expected AccountApprovalException");
        } catch (delivery.portal.service.credits.AccountApprovalException e) {
            Assert.assertEquals(e.code(), "ACCOUNT_NOT_APPROVED");
            Assert.assertEquals(e.approvalStatus(), "PENDING_APPROVAL");
        }

        u.setApprovalStatus(ApprovalStatus.LOCKED);
        users.save(u);
        try {
            approvals.requireApprovedForJobs(u.getId());
            Assert.fail("expected ACCOUNT_LOCKED");
        } catch (delivery.portal.service.credits.AccountApprovalException e) {
            Assert.assertEquals(e.code(), "ACCOUNT_LOCKED");
        }
    }

    @Test
    public void debitIdempotentOnJobId() throws Exception {
        PortalUser u = new PortalUser();
        u.setEmail("rich-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("RichPass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        users.save(u);
        credits.setCredits(u.getId(), "GRANT_DELTA", 100, "pack", "g-" + UUID.randomUUID(), null);

        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path excel = Files.createTempFile("credits-auto", ".xlsx");
        delivery.portal.model.JobRecord job = new delivery.portal.model.JobRecord(
                jobId, "proj_y", u.getId(), "NEW", excel, "https://example.com", "", "", false,
                delivery.portal.model.JobRecord.JobKind.CONVERT);

        credits.tryDebitForJob(job);
        credits.tryDebitForJob(job); // idempotent
        Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 75L); // 100-25
        Assert.assertEquals(credits.userCreditsDto(u.getId()).get("lifetimeSpent"), 25L);
    }

    @Test
    public void executeAndHuntScaleByUnit() throws Exception {
        PortalUser u = new PortalUser();
        u.setEmail("scale-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("ScalePass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        users.save(u);
        credits.setCredits(u.getId(), "GRANT_DELTA", 200, "pack", "s-" + UUID.randomUUID(), null);

        Path excel = Files.createTempFile("credits-two", ".xlsx");
        delivery.excel.ManualTcExcelWriter.write(excel, java.util.List.of(
                new delivery.excel.ManualTestCase("TC_1", "Pay", "", "Open the shop", "The shop opens", "P2", "", "", "", "", ""),
                new delivery.excel.ManualTestCase("TC_2", "Ship", "", "Open the shop", "The shop opens", "P2", "", "", "", "EXECUTE", "")
        ));
        credits.tryDebitForJob(new delivery.portal.model.JobRecord(
                "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "proj_scale", u.getId(), "EXECUTE", excel, "https://example.com", "", "", false,
                delivery.portal.model.JobRecord.JobKind.EXECUTE));
        Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 190L); // 200 - (2 × 5)

        delivery.hunt.HuntRequest hunt = new delivery.hunt.HuntRequest();
        hunt.setIterationCeiling(1);
        hunt.setCycleCeiling(2);
        hunt.setActionCapPerCycle(2);
        Path request = Files.createTempFile("credits-hunt", ".json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(request.toFile(), hunt);
        credits.tryDebitForJob(new delivery.portal.model.JobRecord(
                "hunt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "proj_scale", u.getId(), "HUNT", request, "https://example.com", "", "", false,
                delivery.portal.model.JobRecord.JobKind.HUNT));
        Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 130L); // 190 - (4 × 15)
    }

    @Test
    public void ownerSetsPricesForEveryoneAndANamedAdminCannot() throws Exception {
        String view = mockMvc.perform(get("/pricing")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(view.contains("Write test cases"));
        Assert.assertFalse(view.contains("name=\"cost_"), "pricing is view only");

        mockMvc.perform(post("/admin/pricing")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .with(csrf())
                        .param("label_GENERATE", "Write cases")
                        .param("cost_GENERATE", "12")
                        .param("label_EXECUTE", "Run a test")
                        .param("cost_EXECUTE", "7")
                        .param("label_AUTOMATE", "Build the framework")
                        .param("cost_AUTOMATE", "25")
                        .param("label_HUNT", "Bug Hunter")
                        .param("cost_HUNT", "15"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/account/credits")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costs.generate").value(12))
                .andExpect(jsonPath("$.costs.execute").value(7));

        String memberView = mockMvc.perform(get("/pricing")
                        .with(httpBasic("member-credits@testpilot.local", "MemberPass1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(memberView.contains("Write cases"));
        Assert.assertTrue(memberView.contains("12"));
        Assert.assertFalse(memberView.contains("name=\"cost_"));

        PortalUser deputy = users.findByEmailIgnoreCase("deputy-admin@testpilot.local").orElseGet(() -> {
            PortalUser u = new PortalUser();
            u.setEmail("deputy-admin@testpilot.local");
            u.setPasswordHash(encoder.encode("DeputyPass1!"));
            u.setEnabled(true);
            return u;
        });
        deputy.setRole(PortalUser.Role.ADMIN);
        deputy.setApprovalStatus(ApprovalStatus.APPROVED);
        deputy.setRoleId(rolesService.findByName(SystemRoleNames.ADMIN).orElseThrow().getRoleId());
        users.save(deputy);

        mockMvc.perform(get("/admin/pricing")
                        .with(httpBasic("deputy-admin@testpilot.local", "DeputyPass1!")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users")
                        .with(httpBasic("deputy-admin@testpilot.local", "DeputyPass1!")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/credits/" + member.getId())
                        .with(httpBasic("deputy-admin@testpilot.local", "DeputyPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"GRANT_DELTA\",\"amount\":5,\"note\":\"nope\",\"idempotencyKey\":\"deputy-no\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")
                        .with(httpBasic("deputy-admin@testpilot.local", "DeputyPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/pricing")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .with(csrf())
                        .param("label_GENERATE", "Write test cases")
                        .param("cost_GENERATE", "10")
                        .param("label_EXECUTE", "Run a test")
                        .param("cost_EXECUTE", "5")
                        .param("label_AUTOMATE", "Build the framework")
                        .param("cost_AUTOMATE", "25")
                        .param("label_HUNT", "Bug Hunter")
                        .param("cost_HUNT", "15"))
                .andExpect(status().is3xxRedirection());
    }

    private String memberRoleIdForTest() {
        rolesService.ensureSystemRolesSeeded();
        return rolesService.findByName(SystemRoleNames.MEMBER).orElseThrow().getRoleId();
    }
}