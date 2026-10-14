package delivery.mobile.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class MobileExecuteResultViewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_CANARY = "SECRET_CANARY_OTP_999888";
    private static final String RAW_SERIAL = "emulator-5554";

    @Test
    public void serialFingerprintNeverContainsRawSerial() throws Exception {
        ObservedDeviceIdentity device = ObservedDeviceIdentity.ofHashed(
                "dev-1", "Pixel_6", 34, "arm64-v8a", RAW_SERIAL);
        Assert.assertFalse(device.serialFingerprint().isBlank());
        Assert.assertNotEquals(device.serialFingerprint(), RAW_SERIAL);
        Assert.assertFalse(device.serialFingerprint().contains("emulator"));
        Assert.assertFalse(device.toString().contains(RAW_SERIAL));

        String json = MAPPER.writeValueAsString(device);
        Assert.assertFalse(json.contains(RAW_SERIAL), "JSON must not embed raw serial: " + json);
        Assert.assertTrue(json.contains(device.serialFingerprint()));
    }

    @Test
    public void rawSerialRejectedAsFingerprint() {
        try {
            new ObservedDeviceIdentity("d", "m", 33, "abi", RAW_SERIAL);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("hex hash"));
        }
    }

    @Test
    public void locatorValueRedactedWhenSensitive() throws Exception {
        LocatorResolutionEvidence loc = LocatorResolutionEvidence.of(
                "s1",
                LocatorOriginKind.USER,
                LocatorOriginKind.HIERARCHY,
                "resourceId",
                SECRET_CANARY,
                true,
                "USER_NOT_UNIQUE",
                2);
        Assert.assertEquals(loc.value(), LocatorResolutionEvidence.REDACTED);
        Assert.assertTrue(loc.valueRedacted());
        Assert.assertEquals(loc.requestedOrigin(), LocatorOriginKind.USER);
        Assert.assertEquals(loc.selectedOrigin(), LocatorOriginKind.HIERARCHY);
        Assert.assertEquals(loc.fallbackReason(), "USER_NOT_UNIQUE");
        Assert.assertEquals(loc.matchCount(), 2);

        String json = MAPPER.writeValueAsString(loc);
        Assert.assertFalse(json.contains(SECRET_CANARY));
    }

    @Test
    public void caseResultViewDefaultsAndOutcomes() {
        MobileCaseResultView passed = MobileCaseResultView.passed("TC-1", "att-1");
        Assert.assertEquals(passed.outcome(), CaseOutcomeView.PASSED);
        Assert.assertEquals(passed.setupOutcome(), SetupOutcome.NOT_REQUIRED);
        Assert.assertFalse(passed.containsRawSecret());

        MobileCaseResultView blocked = MobileCaseResultView.blocked("TC-2", "att-1", "SETUP_FAILED");
        Assert.assertEquals(blocked.outcome(), CaseOutcomeView.BLOCKED);
        Assert.assertEquals(blocked.failureCode(), "SETUP_FAILED");

        MobileCaseResultView interrupted = MobileCaseResultView.interrupted("TC-3", "att-1");
        Assert.assertEquals(interrupted.outcome(), CaseOutcomeView.INTERRUPTED_UNCERTAIN);
        Assert.assertEquals(interrupted.failureCode(), "DEVICE_LOST");
    }

    @Test
    public void caseResultViewRefusesRawSecretFlag() {
        try {
            new MobileCaseResultView(
                    "TC", "a", CaseOutcomeView.PASSED, "", SetupOutcome.NOT_REQUIRED,
                    ProofKind.NONE, ProviderKind.NONE, "", 0L, null,
                    ObservedAppIdentity.empty(), ObservedDeviceIdentity.empty(),
                    List.of(), List.of(), true);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("containsRawSecret"));
        }
    }

    @Test
    public void jobResultAggregatesCountsAndRoundTripsJson() throws Exception {
        ObservedAppIdentity app = new ObservedAppIdentity(
                "com.example.app", 42L, "1.2.3", ".MainActivity", "sha256:abcd", AppIdentitySource.APK);
        ObservedDeviceIdentity device = ObservedDeviceIdentity.ofHashed(
                "opaque-dev", "sdk_gphone64", 34, "x86_64", RAW_SERIAL);
        ObservedToolchainIdentity tool = new ObservedToolchainIdentity(
                "2.11.0", "3.0.0", "114.0", "mobile-runner/1");
        RuntimeEvidence runtime = new RuntimeEvidence(app, device, tool, "fence-abc", "input-hash-1");

        FailureEvidenceManifest ev = new FailureEvidenceManifest(
                "s2", "ELEMENT_NOT_FOUND", false, 5000, 120,
                "mem:xml:1", "mem:shot:1", List.of(), "", false, false, false);

        LocatorResolutionEvidence loc = LocatorResolutionEvidence.of(
                "s2", LocatorOriginKind.USER, LocatorOriginKind.HIERARCHY,
                "text", "Login", false, "USER_MISS", 0);

        StepResultEvidence step = new StepResultEvidence(
                "s2", "att-1", CaseOutcomeView.FAILED, "ELEMENT_NOT_FOUND",
                "ref:expected", "ref:observed", false, 0,
                "snap-pre", "snap-post", "NO_CHANGE", 80, false, 200,
                ProviderKind.KEEL, "PRECISION_EXHAUSTED", ProofKind.HIERARCHY,
                loc, "mem:evidence:1");

        MobileCaseResultView failed = new MobileCaseResultView(
                "TC-FAIL", "att-1", CaseOutcomeView.FAILED, "ELEMENT_NOT_FOUND",
                SetupOutcome.PASSED, ProofKind.HIERARCHY, ProviderKind.KEEL, "PRECISION_EXHAUSTED",
                250L, ev, app, device, List.of(loc), List.of(step), false);
        MobileCaseResultView passed = MobileCaseResultView.passed("TC-PASS", "att-1");

        MobileJobResultView job = MobileJobResultView.of(
                "job-1", "att-1", "proj-1", runtime, List.of(passed, failed), 900L);

        Assert.assertEquals(job.passedCount(), 1);
        Assert.assertEquals(job.failedCount(), 1);
        Assert.assertEquals(job.aggregateOutcome(), CaseOutcomeView.FAILED);
        Assert.assertEquals(job.runtime().leaseFence(), "fence-abc");
        Assert.assertEquals(job.runtime().app().packageName(), "com.example.app");
        Assert.assertFalse(job.runtime().device().serialFingerprint().contains("emulator"));

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(job);
        Assert.assertFalse(json.contains(RAW_SERIAL));
        Assert.assertFalse(json.contains(SECRET_CANARY));
        Assert.assertTrue(json.contains("ELEMENT_NOT_FOUND"));
        Assert.assertTrue(json.contains("leaseFence"));

        MobileJobResultView round = MAPPER.readValue(json, MobileJobResultView.class);
        Assert.assertEquals(round.jobId(), "job-1");
        Assert.assertEquals(round.cases().size(), 2);
        Assert.assertEquals(round.runtime().toolchain().appiumVersion(), "2.11.0");
    }

    @Test
    public void enumsParseDefaults() {
        Assert.assertEquals(CaseOutcomeView.parse(null), CaseOutcomeView.TODO);
        Assert.assertEquals(ProofKind.parse(""), ProofKind.NONE);
        Assert.assertEquals(SetupOutcome.parse(null), SetupOutcome.NOT_REQUIRED);
        Assert.assertEquals(ProviderKind.parse("KEEL"), ProviderKind.KEEL);
        Assert.assertEquals(AppIdentitySource.parse("INSTALLED"), AppIdentitySource.INSTALLED);
        Assert.assertEquals(LocatorOriginKind.parse("USER"), LocatorOriginKind.USER);
    }

    @Test
    public void serialFingerprintStable() {
        String a = SerialFingerprint.hash(RAW_SERIAL);
        String b = SerialFingerprint.hash(RAW_SERIAL);
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.length(), 16);
        Assert.assertNotEquals(SerialFingerprint.hash("emulator-5556"), a);
        Assert.assertEquals(SerialFingerprint.hash(""), "");
        Assert.assertEquals(SerialFingerprint.hash(null), "");
    }

    @Test
    public void aggregatePrefersInterruptedOverFailed() {
        MobileCaseResultView failed = MobileCaseResultView.failed("TC-F", "a", "X", null);
        MobileCaseResultView interrupted = MobileCaseResultView.interrupted("TC-I", "a");
        MobileCaseResultView blocked = MobileCaseResultView.blocked("TC-B", "a", "SETUP");
        MobileCaseResultView passed = MobileCaseResultView.passed("TC-P", "a");
        MobileJobResultView job = MobileJobResultView.of(
                "j", "a", "p", RuntimeEvidence.empty(),
                List.of(passed, blocked, failed, interrupted), 1L);
        Assert.assertEquals(job.aggregateOutcome(), CaseOutcomeView.INTERRUPTED_UNCERTAIN);
        Assert.assertEquals(job.interruptedCount(), 1);
        Assert.assertEquals(job.failedCount(), 1);
        Assert.assertEquals(job.blockedCount(), 1);
        Assert.assertEquals(job.passedCount(), 1);
        MobileJobResultView noInterrupt = MobileJobResultView.of(
                "j", "a", "p", RuntimeEvidence.empty(),
                List.of(passed, blocked, failed), 1L);
        Assert.assertEquals(noInterrupt.aggregateOutcome(), CaseOutcomeView.FAILED);
        MobileJobResultView onlyBlocked = MobileJobResultView.of(
                "j", "a", "p", RuntimeEvidence.empty(),
                List.of(passed, blocked), 1L);
        Assert.assertEquals(onlyBlocked.aggregateOutcome(), CaseOutcomeView.BLOCKED);
        MobileJobResultView empty = MobileJobResultView.of(
                "j", "a", "p", RuntimeEvidence.empty(), List.of(), 0L);
        Assert.assertEquals(empty.aggregateOutcome(), CaseOutcomeView.TODO);
    }

}
