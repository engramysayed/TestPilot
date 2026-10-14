package delivery.mobile.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline unit tests for M2-07 result artifact persistence (no Spring context). */
public class MobileJobResultArtifactStoreTest {
    private static final String RAW_SERIAL = "emulator-5554";
    private Path storeRoot;
    private MobileJobResultArtifactStore store;

    @BeforeMethod
    public void setUp() throws Exception {
        storeRoot = Files.createTempDirectory("m207-result-store");
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(storeRoot.toString());
        store = new MobileJobResultArtifactStore(props, new ObjectMapper());
    }

    @Test
    public void writeDoesNotMutateAdmissionSideFile() throws Exception {
        String tenant = "ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String projectId = "proj1";
        String jobId = "job1";
        String admissionJson = "{\"inputHash\":\"frozen-hash-1\",\"appRevisionId\":\"apprev_1\","
                + "\"profileRevisionId\":\"devrev_1\",\"authRevision\":\"auth_1\"}";
        store.writeAdmissionSnapshot(tenant, projectId, jobId, admissionJson);
        byte[] before = store.readAdmissionBytes(tenant, projectId, jobId).orElseThrow();

        ObservedDeviceIdentity device = ObservedDeviceIdentity.ofHashed(
                "opaque-dev", "sdk", 34, "x86_64", RAW_SERIAL);
        RuntimeEvidence runtime = new RuntimeEvidence(
                ObservedAppIdentity.empty(), device, ObservedToolchainIdentity.empty(),
                "fence-1", "frozen-hash-1");
        MobileCaseResultView failed = MobileCaseResultView.failed("TC-1", "att-1", "ELEMENT_NOT_FOUND", null);
        MobileJobResultView view = MobileJobResultView.of(
                jobId, "att-1", projectId, runtime, List.of(failed), 100L);
        store.write(tenant, projectId, view);

        byte[] after = store.readAdmissionBytes(tenant, projectId, jobId).orElseThrow();
        Assert.assertEquals(after, before, "admission-inputs.json must be byte-identical after ingest");
        Assert.assertEquals(new String(after, StandardCharsets.UTF_8), admissionJson);

        MobileJobResultView loaded = store.read(tenant, projectId, jobId).orElseThrow();
        String json = new ObjectMapper().writeValueAsString(loaded);
        Assert.assertFalse(json.contains(RAW_SERIAL));
        Assert.assertTrue(json.contains(device.serialFingerprint()));
        Assert.assertFalse(json.contains("\"containsRawSecret\":true"));
        Assert.assertEquals(loaded.aggregateOutcome(), CaseOutcomeView.FAILED);
        Assert.assertEquals(loaded.runtime().inputHash(), "frozen-hash-1");
    }

    @Test
    public void aggregatePreferenceInterruptedWins() {
        MobileJobResultView view = MobileJobResultView.of(
                "j", "a", "p", RuntimeEvidence.empty(),
                List.of(
                        MobileCaseResultView.passed("p", "a"),
                        MobileCaseResultView.failed("f", "a", "X", null),
                        MobileCaseResultView.blocked("b", "a", "S"),
                        MobileCaseResultView.interrupted("i", "a")),
                1L);
        Assert.assertEquals(view.aggregateOutcome(), CaseOutcomeView.INTERRUPTED_UNCERTAIN);
    }

    @Test
    public void rawSerialRejectedOnDeviceIdentity() {
        try {
            new ObservedDeviceIdentity("d", "m", 34, "abi", RAW_SERIAL);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("hex hash"));
        }
    }
}
