package delivery.portal.api;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:wscollab;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-wscollab",
        "delivery.work-dir=./target/test-delivery-work-wscollab"
})
@AutoConfigureMockMvc
public class WorkspaceCollaborationApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeMethod
    public void extraUsers() {
        ensureUser("member@testpilot.local", "MemberPass1!");
        ensureUser("admin-collab@testpilot.local", "CollabAdmin1!");
    }

    @Test
    public void ownerCanInviteAuditTransferAndRevokeServiceButAdminCannotChangeMembership() throws Exception {
        String projectId = createProject();
        var owner = httpBasic("admin@testpilot.local", "ChangeMeAdmin1!");
        var member = httpBasic("member@testpilot.local", "MemberPass1!");
        var collabAdmin = httpBasic("admin-collab@testpilot.local", "CollabAdmin1!");

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@testpilot.local\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ADDED"));

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin-collab@testpilot.local\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + projectId + "/members")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceRole").value("MEMBER"))
                .andExpect(jsonPath("$.members.length()").value(3));

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .with(member)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@testpilot.local\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .with(collabAdmin)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@testpilot.local\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/" + projectId + "/audit")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.action == 'ADD_MEMBER')]").exists());

        mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"nope\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/" + projectId + "/ownership")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + collabAdminId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSFERRED"));

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/" + projectId + "/services")
                        .with(collabAdmin)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(1));
    }

    @Test
    public void removingMemberCancelsQueuedAndRunningJobsButKeepsCompletedArtifacts() throws Exception {
        String projectId = createProject();
        var owner = httpBasic("admin@testpilot.local", "ChangeMeAdmin1!");
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@testpilot.local\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());

        String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
        JobRecord queued = job("job_revoke_q_" + projectId, projectId, memberId(), JobRecord.Status.QUEUED, tenantId);
        JobRecord running = job("job_revoke_r_" + projectId, projectId, memberId(), JobRecord.Status.RUNNING, tenantId);
        JobRecord done = job("job_revoke_d_" + projectId, projectId, memberId(), JobRecord.Status.COMPLETED, tenantId);
        store.saveJob(queued);
        store.saveJob(running);
        store.saveJob(done);

        mockMvc.perform(delete("/api/projects/" + projectId + "/members/" + memberId())
                        .with(owner)
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledJobs").value(2));

        Assert.assertEquals(store.getJob(queued.getJobId()).orElseThrow().getStatus(), JobRecord.Status.CANCELLED);
        Assert.assertEquals(store.getJob(running.getJobId()).orElseThrow().getStatus(), JobRecord.Status.CANCELLING);
        Assert.assertEquals(store.getJob(done.getJobId()).orElseThrow().getStatus(), JobRecord.Status.COMPLETED);
        Assert.assertTrue(store.getOwnedJob(done.getJobId(), memberId()).isEmpty());
        Assert.assertTrue(store.getOwnedJob(done.getJobId(), adminId()).isPresent());
    }

    @Test
    public void unknownEmailIsStoredAsPendingInvite() throws Exception {
        String projectId = createProject();
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending.collab@testpilot.local\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INVITED"));
        mockMvc.perform(get("/api/projects/" + projectId + "/members")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending[0].email").value("pending.collab@testpilot.local"));
    }

    private JobRecord job(String jobId, String projectId, long owner, JobRecord.Status status, String tenantId) {
        JobRecord job = new JobRecord(
                jobId, projectId, owner, "NEW",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://same.example.com/", "", "", false);
        job.setStatus(status);
        job.setTenantId(tenantId);
        return job;
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Collab Project\",\"baseUrl\":\"https://same.example.com/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private void ensureUser(String email, String password) {
        if (!users.existsByEmailIgnoreCase(email)) {
            PortalUser u = new PortalUser();
            u.setEmail(email);
            u.setPasswordHash(encoder.encode(password));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    private Long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private Long memberId() {
        return users.findByEmailIgnoreCase("member@testpilot.local").orElseThrow().getId();
    }

    private Long collabAdminId() {
        return users.findByEmailIgnoreCase("admin-collab@testpilot.local").orElseThrow().getId();
    }
}
