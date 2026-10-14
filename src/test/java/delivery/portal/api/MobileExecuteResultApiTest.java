package delivery.portal.api;

import delivery.mobile.result.CaseOutcomeView;
import delivery.mobile.result.MobileCaseResultView;
import delivery.mobile.result.MobileJobResultView;
import delivery.mobile.result.ObservedAppIdentity;
import delivery.mobile.result.ObservedDeviceIdentity;
import delivery.mobile.result.ObservedToolchainIdentity;
import delivery.mobile.result.RuntimeEvidence;
import delivery.mobile.result.AppIdentitySource;
import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.service.MobileExecuteResultService;
import delivery.portal.service.PortalStore;
import delivery.mobile.result.MobileJobResultArtifactStore;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-m207-api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=update",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-mobile-m207-api",
        "delivery.work-dir=./target/test-delivery-work-mobile-m207-api",
        "delivery.mobile.enabled=true",
        "delivery.mobile.live-tests=false"
})
@AutoConfigureMockMvc
public class MobileExecuteResultApiTest extends AbstractTestNGSpringContextTests {

    private static final String RAW_SERIAL = "emulator-5554";

    @Autowired MockMvc mockMvc;
    @Autowired PortalStore store;
    @Autowired MobileExecuteResultService results;
    @Autowired MobileJobResultArtifactStore artifactStore;
    @Autowired PortalUserRepository users;
    @Autowired PasswordEncoder encoder;

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }

    private String createAndroidProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M207\",\"baseUrl\":\"https://ex.test/\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private JobRecord seedJob(String projectId, String jobId, String inputHash) {
        ProjectRecord project = store.getOwnedProject(projectId, adminId()).orElseThrow();
        JobRecord job = new JobRecord(jobId, projectId, adminId(), "EXECUTE", Path.of("suite.xlsx"),
                "https://ex.test/", "", "", false, JobRecord.JobKind.EXECUTE);
        job.setTenantId(project.getTenantId());
        job.setInputSnapshotHash(inputHash);
        store.saveJob(job);
        return job;
    }

    @Test
    public void getMobileResult_serialNeverInJson_admissionUnchanged() throws Exception {
        String projectId = createAndroidProject();
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String inputHash = "input-hash-frozen-1";
        JobRecord job = seedJob(projectId, jobId, inputHash);

        String admissionJson = "{\"inputHash\":\"" + inputHash + "\",\"appRevisionId\":\"apprev_x\","
                + "\"deviceProfileRevisionId\":\"devrev_y\",\"caseSettingsRevision\":\"csr_z\"}";
        artifactStore.writeAdmissionSnapshot(job.getTenantId(), projectId, jobId, admissionJson);
        byte[] admissionBefore = artifactStore.readAdmissionBytes(job.getTenantId(), projectId, jobId).orElseThrow();
        String hashBefore = store.getJob(jobId).orElseThrow().getInputSnapshotHash();

        ObservedDeviceIdentity device = ObservedDeviceIdentity.ofHashed(
                "opaque-dev", "sdk_gphone64", 34, "x86_64", RAW_SERIAL);
        RuntimeEvidence runtime = new RuntimeEvidence(
                new ObservedAppIdentity("com.example.app", 42L, "1.2.3", ".Main", "sha256:ab", AppIdentitySource.APK),
                device,
                new ObservedToolchainIdentity("2.11.0", "3.0.0", "114.0", "mobile-runner/1"),
                "fence-abc",
                inputHash);
        MobileJobResultView published = MobileJobResultView.of(
                jobId, "att-1", projectId, runtime,
                List.of(
                        MobileCaseResultView.interrupted("TC-I", "att-1"),
                        MobileCaseResultView.failed("TC-F", "att-1", "ELEMENT_NOT_FOUND", null),
                        MobileCaseResultView.passed("TC-P", "att-1")),
                900L);
        Assert.assertEquals(published.aggregateOutcome(), CaseOutcomeView.INTERRUPTED_UNCERTAIN);

        results.publish(job.getTenantId(), projectId, published);

        Assert.assertEquals(store.getJob(jobId).orElseThrow().getInputSnapshotHash(), hashBefore);
        byte[] admissionAfter = artifactStore.readAdmissionBytes(job.getTenantId(), projectId, jobId).orElseThrow();
        Assert.assertEquals(admissionAfter, admissionBefore);
        Assert.assertEquals(new String(admissionAfter, StandardCharsets.UTF_8), admissionJson);

        MvcResult res = mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + jobId + "/mobile-result")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        String json = res.getResponse().getContentAsString();
        Assert.assertFalse(json.contains(RAW_SERIAL), json);
        Assert.assertFalse(json.contains("emulator"), json);
        Assert.assertTrue(json.contains(device.serialFingerprint()));
        Assert.assertTrue(json.contains("\"aggregateOutcome\":\"INTERRUPTED_UNCERTAIN\""));
        Assert.assertFalse(json.contains("\"containsRawSecret\":true"));
        JSONObject body = new JSONObject(json);
        Assert.assertEquals(body.getString("jobId"), jobId);
        Assert.assertEquals(body.getJSONObject("runtime").getString("inputHash"), inputHash);

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + jobId
                        + "/mobile-result/cases/TC-F")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + jobId
                        + "/mobile-result/cases/MISSING")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void missingResultReturns404() throws Exception {
        String projectId = createAndroidProject();
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        seedJob(projectId, jobId, "h1");
        mockMvc.perform(get("/api/projects/" + projectId + "/jobs/" + jobId + "/mobile-result")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isNotFound());
    }
}
