package delivery.portal.api;

import delivery.portal.PortalApplication;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:jobfilter;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-jobfilter",
        "delivery.work-dir=./target/test-delivery-work-jobfilter"
})
@AutoConfigureMockMvc
public class JobListFilterApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void listJobs_filtersByProjectId() throws Exception {
        String p1 = createProject("P1");
        String p2 = createProject("P2");
        patchBaseUrl(p1);
        patchBaseUrl(p2);
        startJob(p1);
        startJob(p2);

        mockMvc.perform(get("/api/jobs?projectId=" + p1)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(p1));
    }

    @Test
    public void listJobs_filtersByKind() throws Exception {
        String p1 = createProject("KindFilter");
        patchBaseUrl(p1);
        startJob(p1);

        byte[] excel = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile file = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        mockMvc.perform(multipart("/api/projects/" + p1 + "/execute-runs")
                        .file(file)
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/jobs?projectId=" + p1 + "&kind=CONVERT")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobKind").value("CONVERT"));

        mockMvc.perform(get("/api/jobs?projectId=" + p1 + "&kind=EXECUTE")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobKind").value("EXECUTE"))
                .andExpect(jsonPath("$[0].downloadable").value(false));
    }

    @Test
    public void listJobs_unknownProject_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/jobs?projectId=prj_nonexistent")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String createProject(String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    private void patchBaseUrl(String projectId) throws Exception {
        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());
    }

    private void startJob(String projectId) throws Exception {
        byte[] excel = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile file = new MockMultipartFile("excel", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(file)
                        .param("mode", "NEW")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted());
    }
}
