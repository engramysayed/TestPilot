package delivery.portal.api;

import com.sun.net.httpserver.HttpServer;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.PortalStore;
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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:webhook-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.webhook-retry-unit-ms=0",
        "delivery.store-root=./target/test-delivery-store-webhook-api",
        "delivery.work-dir=./target/test-delivery-work-webhook-api"
})
@AutoConfigureMockMvc
public class WebhookApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;

    @Test
    public void configuredWebhookReceivesSignedTerminalJobPayload() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>("");
        AtomicReference<String> sig = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ci", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sig.set(exchange.getRequestHeaders().getFirst("X-Keel-Signature"));
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
            latch.countDown();
        });
        server.start();
        String previousMode = System.getProperty("delivery.install.mode");
        String previousCidrs = System.getProperty("delivery.install.private-cidrs");
        System.setProperty("delivery.install.mode", "dedicated");
        System.setProperty("delivery.install.private-cidrs", "127.0.0.0/8");
        try {
            String projectId = createProject();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ci";
            mockMvc.perform(put("/api/projects/" + projectId + "/webhook")
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                            .header("X-Keel-Requested-With", "Keel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + url + "\",\"secret\":\"ci-secret\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.secretConfigured").value(true));

            mockMvc.perform(get("/api/projects/" + projectId + "/webhook")
                            .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                            .header("X-Keel-Requested-With", "Keel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value(url))
                    .andExpect(jsonPath("$.secret").doesNotExist());

            String tenantId = store.getOwnedProject(projectId, adminId()).orElseThrow().getTenantId();
            JobRecord job = new JobRecord(
                    "job_wh_term", projectId, adminId(), "NEW",
                    Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                    "https://staging.example/", "", "", false, JobRecord.JobKind.EXECUTE);
            job.setTenantId(tenantId);
            job.setStatus(JobRecord.Status.FAILED);
            job.setMessage("assert failed");
            store.saveJob(job);

            Assert.assertTrue(latch.await(5, TimeUnit.SECONDS), "webhook was not delivered");
            Assert.assertTrue(body.get().contains("job_wh_term"));
            Assert.assertTrue(body.get().contains("FAILED"));
            Assert.assertTrue(sig.get() != null && sig.get().startsWith("sha256="));
        } finally {
            restoreProp("delivery.install.mode", previousMode);
            restoreProp("delivery.install.private-cidrs", previousCidrs);
            server.stop(0);
        }
    }

    @Test
    public void metadataWebhookUrlIsRejected() throws Exception {
        String projectId = createProject();
        mockMvc.perform(put("/api/projects/" + projectId + "/webhook")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://169.254.169.254/latest/meta-data/\",\"secret\":\"ci-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    private static void restoreProp(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Webhook Project\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }
}
