package delivery.portal.api;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:wsroles;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-wsroles",
        "delivery.work-dir=./target/test-delivery-work-wsroles"
})
@AutoConfigureMockMvc
public class WorkspaceRoleApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void memberUser() {
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
    public void memberCanReadArtifactsButCannotMutateExecuteExportOrCredentials() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Role Project\",\"baseUrl\":\"https://same.example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        Path root = store.projectDiskRoot(projectId);
        Files.createDirectories(root.resolve("versions"));
        Files.writeString(root.resolve("versions/v1.zip"), "OWNER_ZIP");

        TenantId tenant = TenantId.parse(store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId());
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-wsroles"))
                .addMember(tenant, memberId(), WorkspaceRole.MEMBER);

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/artifacts/download")
                        .param("path", "versions/v1.zip")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/" + projectId + "/artifacts")
                        .param("path", "versions/v1.zip")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/credentials")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"qa\",\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/jobs")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .param("mode", "NEW"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void memberCannotMutateGenerateCompareWorkbookPipelineOrDesignRefs() throws Exception {
        String projectId = createProjectAndAddMember(WorkspaceRole.MEMBER);
        var member = httpBasic("member@testpilot.local", "MemberPass1!");

        mockMvc.perform(post("/api/projects/" + projectId + "/generate-tcs")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a shopper I want checkout so that I can pay.\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/compare")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a shopper I want checkout.\",\"modelA\":\"keel\",\"modelB\":\"cursor\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/compare-async")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a shopper I want checkout.\",\"modelA\":\"keel\",\"modelB\":\"cursor\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/save")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"csv\":\"TC_ID,Title\\nTC_01,Pay\",\"model\":\"keel\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/import")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raw\":\"TC_ID,Title\\nTC_01,Pay\",\"format\":\"csv\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/generate/authoring-review")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"cursor\",\"stories\":\"As a shopper I want checkout.\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/generate-async")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a shopper I want checkout so that I can pay.\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/projects/" + projectId + "/generate-batch-jobs")
                        .file(new MockMultipartFile(
                                "storiesFile", "stories.csv", "text/csv",
                                "story\nAs a shopper I want checkout.".getBytes()))
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/rows")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"tcId\":\"TC_01\",\"keelPath\":\"AUTOMATE\"}]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/coverage-notes")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverageNotes\":\"MEMBER_MUST_NOT_WRITE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/cases/TC_01")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"changed\",\"steps\":\"1. Click Continue\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/" + projectId + "/generated-workbook/cases")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tcIds\":[\"TC_01\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/projects/" + projectId + "/generated-workbook/upload")
                        .file(new MockMultipartFile(
                                "file", "cases.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "not-a-real-xlsx".getBytes()))
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/pipeline")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stories\":\"As a shopper I want checkout.\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/projects/" + projectId + "/design-references")
                        .file(new MockMultipartFile("file", "TC_01.png", "image/png", new byte[]{1, 2, 3, 4}))
                        .param("tcId", "TC_01")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/" + projectId + "/automate-ir")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/projects/" + projectId + "/pre-run-authoring-review")
                        .file(new MockMultipartFile(
                                "excel", "cases.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "not-a-real-xlsx".getBytes()))
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        JobRecord job = new JobRecord(
                "job_role_cancel_" + projectId, projectId, adminId(), "NEW",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://same.example.com/", "", "", false);
        store.saveJob(job);
        mockMvc.perform(post("/api/jobs/job_role_cancel_" + projectId + "/cancel")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/jobs/job_role_cancel_" + projectId + "/force-stop")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void adminMayOperateButCannotDeleteTheProject() throws Exception {
        String projectId = createProjectAndAddMember(WorkspaceRole.ADMIN);
        mockMvc.perform(put("/api/projects/" + projectId + "/generated-workbook/coverage-notes")
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverageNotes\":\"ADMIN_MAY_WRITE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .with(httpBasic("member@testpilot.local", "MemberPass1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());
    }

    private String createProjectAndAddMember(WorkspaceRole role) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Role Mutate Project\",\"baseUrl\":\"https://same.example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
        TenantId tenant = TenantId.parse(store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId());
        WorkspaceDirectory.open(Path.of("./target/test-delivery-store-wsroles"))
                .addMember(tenant, memberId(), role);
        return projectId;
    }

    private Long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private Long memberId() {
        return users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId();
    }
}
