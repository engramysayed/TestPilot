package delivery.portal.api.v2;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import keel.contract.runner.RequestSigner;
import keel.contract.runner.RunnerHeaders;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B06: v2 runner API on 8080 (token + HMAC, attempt / cancel-generation / fencing headers, no loopback trust). */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:runner-api-v2;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-runner-v2",
        "delivery.work-dir=./target/test-delivery-work-runner-v2",
        "keel.runner.v2.chunk-dir=./target/test-runner-v2-chunks"
})
@AutoConfigureMockMvc
public class RunnerApiV2Test extends AbstractTestNGSpringContextTests {
    static final String ADMIN = "admin@testpilot.local";
    static final String PW = "ChangeMeAdmin1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private PortalStore store;
    @Autowired private GeneratedWorkbookService workbooks;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;

    private static RequestPostProcessor loopback() {
        return r -> {
            r.setRemoteAddr("127.0.0.1");
            r.setRemoteHost("localhost");
            return r;
        };
    }

    /** Builds a v2 request signed exactly like the engine will (keel-contract RequestSigner). */
    private static MockHttpServletRequestBuilder signed(String token, HttpMethod method, String path, String query, byte[] body) {
        long ts = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String url = query == null ? path : path + "?" + query;
        MockHttpServletRequestBuilder b = request(method, url)
                .with(loopback())
                .header("Authorization", "Bearer " + token)
                .header("X-Keel-Requested-With", "Keel")
                .header(RunnerHeaders.TIMESTAMP, String.valueOf(ts))
                .header(RunnerHeaders.NONCE, nonce)
                .header(RunnerHeaders.SIGNATURE, RequestSigner.sign(token, method.name(), path, query, ts, nonce, body));
        if (body.length > 0) {
            b.content(body).contentType(method == HttpMethod.PUT ? MediaType.APPLICATION_OCTET_STREAM : MediaType.APPLICATION_JSON);
        }
        return b;
    }

    private static MockHttpServletRequestBuilder fenced(MockHttpServletRequestBuilder b, JSONObject claim, long token) {
        return b.header(RunnerHeaders.ATTEMPT, claim.getString("attemptId"))
                .header(RunnerHeaders.CANCEL_GENERATION, String.valueOf(claim.getLong("cancelGeneration")))
                .header(RunnerHeaders.FENCING_TOKEN, String.valueOf(token));
    }

    private static byte[] json(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void loopbackRequestsWithoutTokenOrSignatureAre401() throws Exception {
        mockMvc.perform(post("/api/v2/runners/claim").with(loopback()).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/runners/claim").with(loopback()).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnauthorized());
        String token = enrollRunner(createProject("Loopback", ADMIN, PW), ADMIN, PW);
        mockMvc.perform(post("/api/v2/runners/claim").with(loopback())
                        .header("Authorization", "Bearer " + token).header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void signatureRejectionsAtTheHttpLayer() throws Exception {
        String token = enrollRunner(createProject("Sig", ADMIN, PW), ADMIN, PW);
        // valid: no queued job -> 204
        MockHttpServletRequestBuilder ok = signed(token, HttpMethod.POST, "/api/v2/runners/claim", null, json("{}"));
        mockMvc.perform(ok).andExpect(status().isNoContent());
        // replay of the exact same signed request
        mockMvc.perform(ok).andExpect(status().isUnauthorized());
        // body tamper: signed "{}", sends "{\"kinds\":[]}"
        mockMvc.perform(signed(token, HttpMethod.POST, "/api/v2/runners/claim", null, json("{}"))
                        .content(json("{\"kinds\":[]}")))
                .andExpect(status().isUnauthorized());
        // skew: timestamp 10 minutes old
        long old = Instant.now().minusSeconds(600).getEpochSecond();
        mockMvc.perform(post("/api/v2/runners/claim").with(loopback())
                        .header("Authorization", "Bearer " + token).header("X-Keel-Requested-With", "Keel")
                        .header(RunnerHeaders.TIMESTAMP, String.valueOf(old)).header(RunnerHeaders.NONCE, "n-old")
                        .header(RunnerHeaders.SIGNATURE, RequestSigner.sign(token, "POST", "/api/v2/runners/claim", null, old, "n-old", new byte[0])))
                .andExpect(status().isUnauthorized());
        // signed with another key
        String other = "tp_run_" + "x".repeat(20);
        long ts = Instant.now().getEpochSecond();
        mockMvc.perform(post("/api/v2/runners/claim").with(loopback())
                        .header("Authorization", "Bearer " + token).header("X-Keel-Requested-With", "Keel")
                        .header(RunnerHeaders.TIMESTAMP, String.valueOf(ts)).header(RunnerHeaders.NONCE, "n-bad")
                        .header(RunnerHeaders.SIGNATURE, RequestSigner.sign(other, "POST", "/api/v2/runners/claim", null, ts, "n-bad", new byte[0])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void claimLeaseStageProgressChunksResultWithFencing() throws Exception {
        String projectId = createProject("V2 Flow", ADMIN, PW);
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "runner-v2");
        String token = enrollRunner(projectId, ADMIN, PW);
        String jobId = submitPrivateJob(projectId, serviceToken(projectId));

        MvcResult claimed = mockMvc.perform(signed(token, HttpMethod.POST, "/api/v2/runners/claim", null,
                        json("{\"capabilities\":[\"BROWSER_CHROME\"],\"kinds\":[\"EXECUTE\"]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.jobKind").value("EXECUTE"))
                .andReturn();
        JSONObject claim = new JSONObject(claimed.getResponse().getContentAsString());
        long fence = claim.getLong("fencingToken");
        Assert.assertTrue(fence >= 1);
        String base = "/api/v2/runners/jobs/" + jobId;

        // fencing headers are mandatory
        mockMvc.perform(signed(token, HttpMethod.POST, base + "/lease", null, new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FENCING_HEADERS_REQUIRED"));
        // stale / wrong fencing token
        mockMvc.perform(fenced(signed(token, HttpMethod.POST, base + "/lease", null, new byte[0]), claim, fence + 41))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_FENCING_TOKEN"));
        // wrong attempt id
        mockMvc.perform(signed(token, HttpMethod.POST, base + "/lease", null, new byte[0])
                        .header(RunnerHeaders.ATTEMPT, "att_forged").header(RunnerHeaders.CANCEL_GENERATION, "0")
                        .header(RunnerHeaders.FENCING_TOKEN, String.valueOf(fence)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEASE_LOST"));
        // wrong cancel generation
        mockMvc.perform(signed(token, HttpMethod.POST, base + "/lease", null, new byte[0])
                        .header(RunnerHeaders.ATTEMPT, claim.getString("attemptId"))
                        .header(RunnerHeaders.CANCEL_GENERATION, String.valueOf(claim.getLong("cancelGeneration") + 1))
                        .header(RunnerHeaders.FENCING_TOKEN, String.valueOf(fence)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_GENERATION_CHANGED"));

        mockMvc.perform(fenced(signed(token, HttpMethod.POST, base + "/lease", null, new byte[0]), claim, fence))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelRequested").value(false));
        mockMvc.perform(fenced(signed(token, HttpMethod.POST, base + "/stage", null, json("{\"stage\":\"BROWSER\"}")), claim, fence))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimStage").value("BROWSER"));
        mockMvc.perform(fenced(signed(token, HttpMethod.POST, base + "/progress", null,
                        json("{\"message\":\"TC 1/1\",\"done\":1,\"total\":1}")), claim, fence))
                .andExpect(status().isAccepted());
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getMessage(), "TC 1/1");
        mockMvc.perform(fenced(signed(token, HttpMethod.GET, base + "/cancel", null, new byte[0]), claim, fence))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelRequested").value(false));

        byte[] p0 = json("PK-part-0|");
        byte[] p1 = json("part-1");
        mockMvc.perform(fenced(signed(token, HttpMethod.PUT, base + "/artifacts/evidence/chunks/0", null, p0), claim, fence))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.complete").value(false));
        mockMvc.perform(fenced(signed(token, HttpMethod.PUT, base + "/artifacts/evidence/chunks/1", "last=true", p1), claim, fence))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.complete").value(true));
        Assert.assertEquals(Files.readString(store.getJob(jobId).orElseThrow().getZipPath()), "PK-part-0|part-1");
        mockMvc.perform(fenced(signed(token, HttpMethod.PUT, base + "/artifacts/..%2Fescape/chunks/0", null, p0), claim, fence))
                .andExpect(r -> Assert.assertTrue(r.getResponse().getStatus() >= 400));

        mockMvc.perform(fenced(signed(token, HttpMethod.POST, base + "/result", null,
                        json("{\"status\":\"COMPLETED\",\"passedCount\":1,\"todoCount\":0,\"message\":\"done\"}")), claim, fence))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        Assert.assertEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.COMPLETED);
        // finished job: lease is gone
        mockMvc.perform(fenced(signed(token, HttpMethod.POST, base + "/lease", null, new byte[0]), claim, fence))
                .andExpect(status().isConflict());
    }

    @Test
    public void crossTenantRunnerGets404ForAnotherTenantsJob() throws Exception {
        String projectId = createProject("Tenant A", ADMIN, PW);
        workbooks.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "Home", "P1", "smoke")), "test", "runner-v2");
        String tokenA = enrollRunner(projectId, ADMIN, PW);
        String jobId = submitPrivateJob(projectId, serviceToken(projectId));
        JSONObject claim = new JSONObject(mockMvc.perform(signed(tokenA, HttpMethod.POST, "/api/v2/runners/claim", null, json("{}")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        ensureUser("v2-other@testpilot.local", "OtherUser1!");
        String tokenB = enrollRunner(createProject("Tenant B", "v2-other@testpilot.local", "OtherUser1!"),
                "v2-other@testpilot.local", "OtherUser1!");
        mockMvc.perform(fenced(signed(tokenB, HttpMethod.POST, "/api/v2/runners/jobs/" + jobId + "/lease", null, new byte[0]),
                        claim, claim.getLong("fencingToken")))
                .andExpect(status().isNotFound());
        mockMvc.perform(fenced(signed(tokenB, HttpMethod.POST, "/api/v2/runners/jobs/" + jobId + "/result", null,
                        json("{\"status\":\"COMPLETED\"}")), claim, claim.getLong("fencingToken")))
                .andExpect(status().isNotFound());
        Assert.assertNotEquals(store.getJob(jobId).orElseThrow().getStatus(), JobRecord.Status.COMPLETED);
    }

    private String serviceToken(String projectId) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/services")
                        .with(httpBasic(ADMIN, PW)).header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"ci\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).getString("token");
    }

    private String enrollRunner(String projectId, String email, String password) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects/" + projectId + "/runners")
                        .with(httpBasic(email, password)).header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"engine-1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).getString("token");
    }

    private String submitPrivateJob(String projectId, String svc) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/v1/projects/" + projectId + "/jobs")
                        .header("Authorization", "Bearer " + svc).header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"EXECUTE\",\"runner\":\"private\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).getString("jobId");
    }

    private String createProject(String name, String email, String password) throws Exception {
        return new JSONObject(mockMvc.perform(post("/api/projects")
                        .with(httpBasic(email, password)).header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).getString("projectId");
    }

    private void ensureUser(String email, String password) {
        if (!users.existsByEmailIgnoreCase(email)) {
            PortalUser u = new PortalUser();
            u.setEmail(email);
            u.setPasswordHash(encoder.encode(password));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }
}
