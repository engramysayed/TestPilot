package delivery.portal.jobs;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B08 flag flip: keel.jobs.dispatch.execute=remote routes a normally in-process EXECUTE job to the engine. */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:dispatch-remote;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-dispatch",
        "delivery.work-dir=./target/test-delivery-work-dispatch",
        "keel.jobs.dispatch.execute=remote"
})
@AutoConfigureMockMvc
public class RemoteDispatchFlagTest extends AbstractTestNGSpringContextTests {
    @Autowired MockMvc mockMvc;
    @Autowired PortalStore store;
    @Autowired GeneratedWorkbookService workbooks;
    @Autowired JobDispatchPolicy policy;

    private JSONObject postJson(String path, String body, String auth) throws Exception {
        var req = post(path).header("X-Keel-Requested-With", "Keel").contentType(MediaType.APPLICATION_JSON).content(body);
        req = auth == null ? req.with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")) : req.header("Authorization", auth);
        return new JSONObject(mockMvc.perform(req).andExpect(r -> Assert.assertTrue(r.getResponse().getStatus() < 300,
                path + " " + r.getResponse().getContentAsString())).andReturn().getResponse().getContentAsString());
    }

    @Test
    public void executeJobWithoutRunnerFlagIsDispatchedRemote() throws Exception {
        Assert.assertTrue(policy.remote(delivery.portal.model.JobRecord.JobKind.EXECUTE));
        Assert.assertFalse(policy.remote(delivery.portal.model.JobRecord.JobKind.CONVERT));
        String projectId = postJson("/api/projects", "{\"name\":\"Dispatch\",\"baseUrl\":\"https://staging.example/\"}", null)
                .getString("projectId");
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "dispatch");
        String svc = postJson("/api/projects/" + projectId + "/services", "{\"label\":\"ci\",\"role\":\"ADMIN\"}", null).getString("token");
        String jobId = postJson("/api/v1/projects/" + projectId + "/jobs", "{\"kind\":\"EXECUTE\"}", "Bearer " + svc).getString("jobId");
        Assert.assertTrue(store.getJob(jobId).orElseThrow().isRequirePrivateRunner(), "remote dispatch flags the job for the engine");
        Assert.assertTrue(store.beginWork(jobId).isEmpty(), "in-process workers skip remote jobs");
    }
}
