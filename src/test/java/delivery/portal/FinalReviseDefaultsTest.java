package delivery.portal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:finalrevisedefaults;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-finalrevisedefaults",
        "delivery.work-dir=./target/test-delivery-work-finalrevisedefaults"
})
@AutoConfigureMockMvc
public class FinalReviseDefaultsTest extends AbstractTestNGSpringContextTests {

    private static final Pattern ENABLED_FALSE = Pattern.compile(
            "^delivery\\.final-revise\\.enabled=false\\s*$", Pattern.MULTILINE);

    @Autowired
    private DeliveryPortalProperties props;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void propertiesClass_defaultsFinalReviseDisabled() {
        Assert.assertFalse(new DeliveryPortalProperties().isFinalReviseEnabled());
    }

    @Test
    public void applicationProperties_bindsFinalReviseDisabled() {
        Assert.assertFalse(props.isFinalReviseEnabled());
    }

    @Test
    public void applicationProperties_fileHasFinalReviseDisabled() throws Exception {
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(ENABLED_FALSE.matcher(text).find(),
                    "application.properties must set delivery.final-revise.enabled=false");
        }
    }

    @Test
    public void uploadPage_exposesFinalReviseEnabledFlag() throws Exception {
        String body = mockMvc.perform(get("/upload")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assert.assertTrue(body.contains("data-final-revise-enabled=\"false\""));
        Assert.assertTrue(body.contains("id=\"finalReviseDisabledWarn\""));
    }

    @Test
    public void createJob_forcesFinalReviseOffWhenDisabled() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Final revise off\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").exists())
                .andReturn();
        String projectId = new org.json.JSONObject(created.getResponse().getContentAsString())
                .getString("projectId");

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com/\"}"))
                .andExpect(status().isOk());

        byte[] excelBytes = Files.readAllBytes(Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"));
        MockMultipartFile excel = new MockMultipartFile("excel", "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        MvcResult jobRes = mockMvc.perform(multipart("/api/projects/" + projectId + "/jobs")
                        .file(excel)
                        .param("mode", "NEW")
                        .param("finalRevise", "true")
                        .header("X-Keel-Requested-With", "Keel")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn();
        String jobId = new org.json.JSONObject(jobRes.getResponse().getContentAsString()).getString("jobId");

        mockMvc.perform(get("/api/jobs/" + jobId)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalRevise").value(false));
    }
}
