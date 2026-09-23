package delivery.portal.web;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.PortalUserRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:evidencemvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-evidencemvc",
        "delivery.work-dir=./target/test-delivery-work-evidencemvc"
})
@AutoConfigureMockMvc
public class EvidenceMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    @Test
    public void evidencePage_rendersForExecuteJob() throws Exception {
        String projectId = createProject();
        Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
        String jobId = "exec_evidence_mvc";
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("EXECUTE");
        job.setJobKind("EXECUTE");
        job.setStatus("FAILED");
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        String body = mockMvc.perform(get("/evidence?jobId=" + jobId + "&tcId=TC_001")
                        .with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("Evidence triage"));
        Assert.assertTrue(body.contains("run-compare-panel"));
        Assert.assertTrue(body.contains("ir-block"));
    }

    @Test
    public void evidencePage_redirectsForNonExecuteJob() throws Exception {
        String projectId = createProject();
        Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
        String jobId = "conv_evidence_mvc";
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("NEW");
        job.setJobKind("CONVERT");
        job.setStatus("COMPLETED");
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        mockMvc.perform(get("/evidence?jobId=" + jobId)
                        .with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().is3xxRedirection());
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EvidenceMvc\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
