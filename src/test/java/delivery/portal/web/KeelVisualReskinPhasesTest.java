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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:keelreskinphases;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-keelreskinphases",
        "delivery.work-dir=./target/test-delivery-work-keelreskinphases"
})
@AutoConfigureMockMvc
public class KeelVisualReskinPhasesTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    private String projectId;

    @BeforeClass
    public void createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reskin Demo\",\"baseUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        projectId = new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void phase4_projectsAndDetail_useKeelChrome() throws Exception {
        String projects = mockMvc.perform(get("/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assert.assertTrue(projects.contains("class=\"shell keel-page\""));
        Assert.assertTrue(projects.contains("class=\"projectcards\""));
        Assert.assertTrue(projects.contains("projects-filter"));

        String detail = mockMvc.perform(get("/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assert.assertTrue(detail.contains("class=\"project-tabs tabs\""));
        Assert.assertTrue(detail.contains("class=\"summary-strip\""));
    }

    @Test
    public void phase5_workflowPages_useKeelChrome() throws Exception {
        for (String path : new String[]{"/generate", "/execute", "/automate"}) {
            String body = mockMvc.perform(get(path)
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Assert.assertTrue(body.contains("class=\"shell keel-page\""), path);
            Assert.assertTrue(body.contains("workspace-launcher"), path);
            Assert.assertTrue(body.contains("keel-reskin.css"), path);
        }
    }

    @Test
    public void phase6_runsCluster_useKeelChrome() throws Exception {
        Long ownerId = users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
        String listJobId = "keel_reskin_runs_list";
        JobEntity listed = new JobEntity();
        listed.setJobId(listJobId);
        listed.setProjectId(projectId);
        listed.setOwnerUserId(ownerId);
        listed.setMode("NEW");
        listed.setJobKind("CONVERT");
        listed.setStatus("COMPLETED");
        listed.setProgressCurrent(1);
        listed.setProgressTotal(1);
        listed.setCreatedAt(Instant.now());
        listed.setCompletedAt(Instant.now());
        jobRepository.save(listed);

        String runs = mockMvc.perform(get("/runs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(runs.contains("keel-page"));
        Assert.assertTrue(runs.contains("keel-reskin.css"));
        Assert.assertTrue(runs.contains("badge--pass"), "completed run should render badge--pass");

        String jobId = "keel_reskin_status_smoke";
        JobEntity job = new JobEntity();
        job.setJobId(jobId);
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("EXECUTE");
        job.setJobKind("EXECUTE");
        job.setStatus("RUNNING");
        job.setProgressCurrent(0);
        job.setProgressTotal(1);
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        String status = mockMvc.perform(get("/status").param("jobId", jobId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(status.contains("keel-page"));
        Assert.assertTrue(status.contains("progress-track"));
    }

    @Test
    public void phase7_secondaryPages_useKeelChrome() throws Exception {
        String packages = mockMvc.perform(get("/packages")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(packages.contains("keel-page"));

        String guide = mockMvc.perform(get("/guide")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(guide.contains("keel-page"));
        Assert.assertTrue(guide.contains("class=\"heading guide-hero"));

        String account = mockMvc.perform(get("/account")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(account.contains("keel-page"));
    }

    @Test
    public void phase8_keelStylesheetLinkedOnAllPages() throws Exception {
        String dashboard = mockMvc.perform(get("/dashboard")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Assert.assertTrue(dashboard.contains("keel-reskin.css"));
    }
}
