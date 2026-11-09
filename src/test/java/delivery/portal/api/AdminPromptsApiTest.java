package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.RolesService;
import delivery.portal.roles.SystemRoleNames;
import delivery.prompts.PromptCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portaladminprompts;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.mail-enabled=false",
        "delivery.store-root=./target/test-delivery-store-admin-prompts",
        "delivery.work-dir=./target/test-delivery-work-admin-prompts"
})
@AutoConfigureMockMvc
public class AdminPromptsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RolesService rolesService;

    private PortalUser member;

    @AfterMethod
    public void clearOverride() {
        PromptCatalog.setOverrideDir(null);
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.clearCache();
    }

    @BeforeMethod
    public void ensureMember() {
        member = users.findByEmailIgnoreCase("member-prompts@testpilot.local").orElseGet(() -> {
            PortalUser u = new PortalUser();
            u.setEmail("member-prompts@testpilot.local");
            u.setPasswordHash(encoder.encode("MemberPass1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            u.setApprovalStatus(ApprovalStatus.APPROVED);
            return users.save(u);
        });
        if (member.getApprovalStatus() != ApprovalStatus.APPROVED
                || member.getRoleId() == null
                || member.getRoleId().isBlank()) {
            member.setApprovalStatus(ApprovalStatus.APPROVED);
            rolesService.ensureSystemRolesSeeded();
            member.setRoleId(rolesService.findByName(SystemRoleNames.MEMBER).orElseThrow().getRoleId());
            users.save(member);
        }
    }

    @Test
    public void nonHeadForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/prompts")
                        .with(httpBasic("member-prompts@testpilot.local", "MemberPass1!")))
                .andExpect(status().isForbidden());
    }

    @Test
    public void ownerListAndGetClasspath() throws Exception {
        mockMvc.perform(get("/api/admin/prompts")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.promptsVersion").value("2026.11.07"));

        mockMvc.perform(get("/api/admin/prompts/heal/pick")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("heal/pick"))
                .andExpect(jsonPath("$.source").value("classpath"));
    }

    @Test
    public void ownerPutWithoutDir_returns400() throws Exception {
        PromptCatalog.setOverrideDir(null);
        mockMvc.perform(put("/api/admin/prompts/heal/pick")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PROMPTS_DIR_UNSET"));
    }

    @Test
    public void ownerPutWithTempDir_roundTrip() throws Exception {
        Path dir = Files.createTempDirectory("keel-admin-prompts-api");
        PromptCatalog.setOverrideDir(dir.toAbsolutePath());
        mockMvc.perform(put("/api/admin/prompts/heal/pick")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"API_OVERRIDE\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("override"))
                .andExpect(jsonPath("$.text").value("API_OVERRIDE\n"));

        mockMvc.perform(get("/api/admin/prompts/heal/pick/diff")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identical").value(false));
    }
}
