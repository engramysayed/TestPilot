package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.worker.GenerateBatchWorker;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:generatebatchapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-generatebatchapi",
        "delivery.work-dir=./target/test-delivery-work-generatebatchapi"
})
@AutoConfigureMockMvc
public class GenerateBatchApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GenerateBatchWorker worker;

    @Test
    public void createBatchJob_acceptsValidCsv() throws Exception {
        doNothing().when(worker).submit(anyString());
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "storiesFile",
                "stories.csv",
                "text/csv",
                """
                        US_ID,Title,Story
                        US_001,Login,As a user I want to log in.
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/projects/" + projectId + "/generate-batch-jobs")
                        .file(file)
                        .param("model", "qwen2.5:latest")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    public void createBatchJob_rejectsUnknownModel() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "storiesFile",
                "stories.csv",
                "text/csv",
                """
                        US_ID,Title,Story
                        US_001,Login,As a user I want to log in.
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/projects/" + projectId + "/generate-batch-jobs")
                        .file(file)
                        .param("model", "not-a-real-model")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MODEL"));
    }

    @Test
    public void createBatchJob_rejectsInvalidCsv() throws Exception {
        String projectId = createProject();
        MockMultipartFile file = new MockMultipartFile(
                "storiesFile",
                "bad.csv",
                "text/csv",
                "Title,Story\nx,y".getBytes()
        );

        mockMvc.perform(multipart("/api/projects/" + projectId + "/generate-batch-jobs")
                        .file(file)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Batch Generate\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
