package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.roles.RolesService;
import delivery.portal.roles.SystemRoleNames;
import delivery.portal.service.llm.LlmSettingsService;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:portalllmsettings;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.mail-enabled=false",
        "delivery.store-root=./target/test-delivery-store-llm-settings",
        "delivery.work-dir=./target/test-delivery-work-llm-settings"
})
@AutoConfigureMockMvc
public class AdminLlmSettingsApiTest extends AbstractTestNGSpringContextTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RolesService rolesService;
    @Autowired private LlmSettingsService llmSettings;

    private PortalUser member;

    @BeforeMethod
    public void ensureMember() {
        member = users.findByEmailIgnoreCase("member-llm@testpilot.local").orElseGet(() -> {
            PortalUser u = new PortalUser();
            u.setEmail("member-llm@testpilot.local");
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
            member.setRoleId(memberRoleIdForTest());
            users.save(member);
        }
    }

    private String memberRoleIdForTest() {
        rolesService.ensureSystemRolesSeeded();
        return rolesService.findByName(SystemRoleNames.MEMBER).orElseThrow().getRoleId();
    }

    @Test
    public void nonHeadForbiddenOnGetAndPut() throws Exception {
        mockMvc.perform(get("/api/admin/llm-settings")
                        .with(httpBasic("member-llm@testpilot.local", "MemberPass1!")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/llm-settings")
                        .with(httpBasic("member-llm@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ollamaChatModel\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/llm-settings/ollama/models")
                        .with(httpBasic("member-llm@testpilot.local", "MemberPass1!")))
                .andExpect(status().isForbidden());
    }

    @Test
    public void ownerPutSecretMaskedOnGetBlankLeavesSecret() throws Exception {
        String secret = "owner-secret-" + UUID.randomUUID() + "-ZZ41";
        mockMvc.perform(put("/api/admin/llm-settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cursorApiKey\":\"" + secret + "\",\"ollamaChatModel\":\"gemma4:e2b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursorApiKeyConfigured").value(true))
                .andExpect(jsonPath("$.cursorApiKeyLast4").value("ZZ41"))
                .andExpect(jsonPath("$.ollamaChatModel").value("gemma4:e2b"));

        MvcResult get1 = mockMvc.perform(get("/api/admin/llm-settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursorApiKeyConfigured").value(true))
                .andExpect(jsonPath("$.cursorApiKeyLast4").value("ZZ41"))
                .andReturn();
        String body = get1.getResponse().getContentAsString();
        Assert.assertFalse(body.contains(secret), "GET must never echo raw secret");
        Assert.assertFalse(new JSONObject(body).has("cursorApiKey"));

        mockMvc.perform(put("/api/admin/llm-settings")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cursorApiKey\":\"\",\"ollamaChatModel\":\"qwen2.5:latest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursorApiKeyConfigured").value(true))
                .andExpect(jsonPath("$.cursorApiKeyLast4").value("ZZ41"))
                .andExpect(jsonPath("$.ollamaChatModel").value("qwen2.5:latest"));

        assertCursorStillConfigured();
    }

    private void assertCursorStillConfigured() {
        var view = llmSettings.maskedView();
        Assert.assertEquals(view.get("cursorApiKeyConfigured"), Boolean.TRUE);
        Assert.assertEquals(view.get("cursorApiKeyLast4"), "ZZ41");
    }

    @Test
    public void ollamaModelsUnreachableFailsSoftWith502() throws Exception {
        mockMvc.perform(get("/api/admin/llm-settings/ollama/models")
                        .param("baseUrl", "http://127.0.0.1:9")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("OLLAMA_UNREACHABLE"));
    }
}
