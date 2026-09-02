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
        "spring.datasource.url=jdbc:h2:mem:dashboardmvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-dashboardmvc",
        "delivery.work-dir=./target/test-delivery-work-dashboardmvc"
})
@AutoConfigureMockMvc
public class DashboardMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    private String fetchDashboard() throws Exception {
        return mockMvc.perform(get("/dashboard").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    public void dashboard_runningConversion_showsBadgeSpinner() throws Exception {
        String projectId = createProject();
        Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
        JobEntity job = new JobEntity();
        job.setJobId("conv_dash_busy_mvc");
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("NEW");
        job.setJobKind("CONVERT");
        job.setStatus("RUNNING");
        job.setProgressCurrent(1);
        job.setProgressTotal(3);
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        String body = fetchDashboard();

        Assert.assertTrue(body.contains("conv_dash_busy_mvc"));
        int jobIdIdx = body.indexOf("conv_dash_busy_mvc");
        int trStart = body.lastIndexOf("<tr", jobIdIdx);
        Assert.assertTrue(trStart >= 0, "no table row precedes seeded job id");
        int trEnd = body.indexOf("</tr>", trStart);
        Assert.assertTrue(trEnd >= 0, "table row not closed");
        String jobRow = body.substring(trStart, trEnd + "</tr>".length());
        Assert.assertTrue(jobRow.contains("spinner--badge"), "running job badge should include spinner");
    }

    @Test
    public void dashboard_rendersStatsOnly() throws Exception {
        String body = fetchDashboard();

        Assert.assertTrue(body.contains("Results by day"));
        Assert.assertTrue(body.contains("Job status"));
        Assert.assertTrue(body.contains("Automate results"));
        Assert.assertTrue(body.contains("Pass rate"));
        Assert.assertTrue(body.contains("Execute runs"));
        Assert.assertFalse(body.contains("class=\"verb-cards"),
                "dashboard should not show verb navigation cards");
        Assert.assertFalse(body.contains("Open Generate"));
        Assert.assertFalse(body.contains("Open Execute"));
        Assert.assertFalse(body.contains("Open Automate"));
    }

    @Test
    public void dashboard_hasChartCanvases() throws Exception {
        String body = fetchDashboard();

        Assert.assertTrue(body.contains("id=\"scoreChart\""));
        Assert.assertTrue(body.contains("id=\"statusChart\""));
        Assert.assertTrue(body.contains("id=\"passChart\""));
    }

    @Test
    public void dashboard_noStaleUploadCta() throws Exception {
        String body = fetchDashboard();

        Assert.assertFalse(body.contains("href=\"/upload\">New conversion"));
        Assert.assertFalse(body.contains("Start in Automate"));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dashboard MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
