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
        "spring.datasource.url=jdbc:h2:mem:statuspagemvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-statuspagemvc",
        "delivery.work-dir=./target/test-delivery-work-statuspagemvc"
})
@AutoConfigureMockMvc
public class StatusPageMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    @Test
    public void statusPage_executeJob_hidesZipDownload() throws Exception {
        String projectId = createProject();
        Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
        String jobId = "exec_status_mvc_test";
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("EXECUTE");
        job.setJobKind("EXECUTE");
        job.setStatus("COMPLETED");
        job.setPassedCount(1);
        job.setTodoCount(0);
        job.setProgressCurrent(1);
        job.setProgressTotal(1);
        job.setCreatedAt(Instant.now());
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        String body = mockMvc.perform(get("/status").param("jobId", jobId)
                        .with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("data-job-kind=\"EXECUTE\""));
        Assert.assertTrue(body.contains("View results on Execute"));
        int downloadWrap = body.indexOf("id=\"download-wrap\"");
        Assert.assertTrue(downloadWrap >= 0, "download-wrap missing");
        int downloadWrapEnd = body.indexOf('>', downloadWrap);
        String downloadWrapTag = body.substring(downloadWrap, downloadWrapEnd);
        Assert.assertTrue(downloadWrapTag.contains("hidden"), "ZIP download should be hidden for execute jobs");
    }

    @Test
    public void statusPage_runningGenerateBatch_showsBusySpinnerAndElapsed() throws Exception {
        String projectId = createProject();
        Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
        String jobId = "genb_status_busy_mvc";
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("GENERATE_BATCH");
        job.setJobKind("GENERATE_BATCH");
        job.setStatus("RUNNING");
        job.setMessage("Generating US_ASYNC_001 — story");
        job.setPassedCount(0);
        job.setTodoCount(0);
        job.setProgressCurrent(1);
        job.setProgressTotal(1);
        Instant created = Instant.parse("2026-08-31T10:00:00Z");
        job.setCreatedAt(created);
        jobRepository.save(job);

        String body = mockMvc.perform(get("/status").param("jobId", jobId)
                        .with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("id=\"job-busy-spinner\""), "busy spinner missing");
        Assert.assertTrue(body.contains("id=\"job-elapsed\""), "elapsed line missing");
        Assert.assertTrue(body.contains("data-created-at=\"2026-08-31T10:00:00Z\""),
                "status page must stamp job createdAt so refresh does not reset the timer");

        String jobJson = mockMvc.perform(get("/api/jobs/" + jobId)
                        .with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(jobJson.contains("\"createdAt\""), jobJson);
        Assert.assertTrue(jobJson.contains("2026-08-31T10:00:00Z"), jobJson);
        Assert.assertTrue(body.contains("aria-busy=\"true\""), "live progress should be aria-busy while RUNNING");
        Assert.assertTrue(body.contains("progress-track--indeterminate"), "bar should be indeterminate while RUNNING");
        int spinner = body.indexOf("id=\"job-busy-spinner\"");
        String spinnerTag = body.substring(spinner, body.indexOf('>', spinner));
        Assert.assertTrue(spinnerTag.contains("aria-hidden=\"true\""), "spinner should be aria-hidden");
        String spinnerTagNoAria = spinnerTag.replace("aria-hidden", "");
        Assert.assertFalse(spinnerTagNoAria.contains(" hidden")
                        || spinnerTagNoAria.contains("hidden=")
                        || spinnerTagNoAria.contains("hidden>"),
                "spinner must be visible while RUNNING");
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Status Page MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
