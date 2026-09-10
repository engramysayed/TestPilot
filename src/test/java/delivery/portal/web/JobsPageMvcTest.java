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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:jobspagemvc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-jobspagemvc",
        "delivery.work-dir=./target/test-delivery-work-jobspagemvc"
})
@AutoConfigureMockMvc
public class JobsPageMvcTest extends AbstractTestNGSpringContextTests {

    private static final String AUTH_USER = "admin@testpilot.local";
    private static final String AUTH_PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PortalUserRepository users;

    @Test
    public void jobsPage_runningConversion_showsBadgeSpinner() throws Exception {
        String projectId = createProject();
        Long ownerId = users.findByEmailIgnoreCase(AUTH_USER).orElseThrow().getId();
        JobEntity job = new JobEntity();
        job.setJobId("conv_jobs_busy_mvc");
        job.setProjectId(projectId);
        job.setOwnerUserId(ownerId);
        job.setMode("NEW");
        job.setJobKind("CONVERT");
        job.setStatus("RUNNING");
        job.setProgressCurrent(1);
        job.setProgressTotal(3);
        job.setCreatedAt(Instant.now());
        jobRepository.save(job);

        String body = mockMvc.perform(get("/runs").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("conv_jobs_busy_mvc"));
        String jobRow = tableRowContaining(body, "conv_jobs_busy_mvc");
        Assert.assertTrue(jobRow.contains("spinner--badge"), "running job badge should include spinner");
    }

    private static String tableRowContaining(String html, String marker) {
        int from = 0;
        while (true) {
            int trStart = html.indexOf("<tr", from);
            if (trStart < 0) {
                break;
            }
            int trEnd = html.indexOf("</tr>", trStart);
            if (trEnd < 0) {
                break;
            }
            String row = html.substring(trStart, trEnd + "</tr>".length());
            if (row.contains(marker)) {
                return row;
            }
            from = trEnd + "</tr>".length();
        }
        Assert.fail("no table row contains: " + marker);
        return null;
    }

    @Test
    public void runsPage_showsExecuteAndAutomate() throws Exception {
        String projectId = createProject();
        patchBaseUrl(projectId);
        String execJobId = startExecuteRun(projectId);

        String body = mockMvc.perform(get("/runs").with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains(execJobId), "execute run should appear on /runs");
        Assert.assertTrue(body.contains("Execute"));
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jobs Page MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    private void patchBaseUrl(String projectId) throws Exception {
        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());
    }

    private String startExecuteRun(String projectId) throws Exception {
        byte[] excel = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile file = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        MvcResult res = mockMvc.perform(multipart("/api/projects/" + projectId + "/execute-runs")
                        .file(file)
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic(AUTH_USER, AUTH_PASS)))
                .andExpect(status().isAccepted())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("jobId");
    }
}
