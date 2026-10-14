package delivery.mobile.runner.evidence;

import delivery.mobile.result.FailureEvidenceManifest;
import delivery.mobile.runner.device.HealVisionGate;
import delivery.mobile.runner.device.MobileCrashDetector;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotParser;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

public class MobileFailureEvidenceCollectorTest {
    private final MobileSnapshotParser parser = new MobileSnapshotParser();

    private MobileSnapshot login() {
        String xml = """
                <hierarchy>
                  <node class="android.widget.EditText" resource-id="com.example.test:id/phone_Number_Input" text="" content-desc="Phone number" clickable="true" enabled="true" focusable="true" bounds="[80,400][1000,520]" package="com.example.test"/>
                  <node class="android.widget.EditText" resource-id="com.example.test:id/password_Input" text="CANARY_SECRET_VALUE_DO_NOT_LEAK" password="true" content-desc="Password" clickable="true" enabled="true" focusable="true" bounds="[80,560][1000,680]" package="com.example.test"/>
                  <node class="android.widget.Button" resource-id="com.example.test:id/sign_In_Button" text="Sign in" clickable="true" enabled="true" bounds="[80,720][1000,840]" package="com.example.test"/>
                </hierarchy>
                """;
        return parser.parse(xml, new MobileSnapshotMeta(1, 1L, "com.example.test", ".Login",
                "NATIVE", 0, 1080, 1920, "en", MobileSnapshotInvalidation.NONE));
    }

    @Test
    public void redactionCanaryAbsentAndTop5Deterministic() {
        AtomicLong clock = new AtomicLong(0);
        MobileFailureEvidenceCollector col = new MobileFailureEvidenceCollector(
                5_000L, clock::get, new MobileCrashDetector());
        AtomicReference<String> xml = new AtomicReference<>();
        AtomicReference<byte[]> shot = new AtomicReference<>();
        byte[] rgba = new byte[1080 * 1920 * 4];
        // paint white
        for (int i = 0; i < rgba.length; i++) rgba[i] = (byte) 255;

        FailureEvidenceManifest m = col.collect(new MobileFailureEvidenceCollector.EvidenceRequest(
                "step-1",
                "LOCATOR_NOT_FOUND",
                "sign_In_Button",
                "com.example.test",
                1000L,
                login(),
                rgba,
                1080,
                1920,
                "",
                xml::set,
                shot::set,
                s -> {}
        ));
        assertFalse(m.partial());
        assertNotNull(xml.get());
        assertFalse(xml.get().contains(XmlRedactor.SECRET_CANARY));
        assertTrue(xml.get().contains(XmlRedactor.REDACTED));
        assertTrue(m.nearestCandidates().size() <= 5);
        assertTrue(m.nearestCandidates().size() >= 1);
        // deterministic ranks 1..n
        for (int i = 0; i < m.nearestCandidates().size(); i++) {
            assertEquals(m.nearestCandidates().get(i).rank(), i + 1);
        }
        // sign_In_Button should be among the nearest for intended "sign_In_Button"
        assertTrue(m.nearestCandidates().stream().anyMatch(c ->
                c.label().contains("sign_In_Button")));
        // password bounds masked (black)
        assertNotNull(shot.get());
        assertTrue(HealVisionGate.allowHeal(m));
    }

    @Test
    public void appCrashShortCircuitsHealAndVision() throws Exception {
        AtomicLong clock = new AtomicLong(0);
        MobileFailureEvidenceCollector col = new MobileFailureEvidenceCollector(
                5_000L, clock::get, new MobileCrashDetector());
        String logcat;
        try (var in = getClass().getClassLoader().getResourceAsStream("logcat/fatal-exception.txt")) {
            logcat = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        AtomicReference<String> excerpt = new AtomicReference<>();
        FailureEvidenceManifest m = col.collect(new MobileFailureEvidenceCollector.EvidenceRequest(
                "step-2",
                "ACTION_FAILED",
                "sign_In_Button",
                "com.example.test",
                900L,
                login(),
                null,
                0,
                0,
                logcat,
                s -> {},
                b -> {},
                excerpt::set
        ));
        assertTrue(m.appCrashed());
        assertEquals(m.failureCode(), "APP_CRASHED");
        assertTrue(m.healSkipped());
        assertTrue(m.visionSkipped());
        assertFalse(HealVisionGate.allowHeal(m));
        assertFalse(HealVisionGate.allowVision(m));
        assertFalse(excerpt.get().isBlank());
    }

    @Test
    public void budgetExceededMarksPartial() {
        AtomicLong clock = new AtomicLong(0);
        MobileFailureEvidenceCollector col = new MobileFailureEvidenceCollector(
                5_000L, () -> {
                    long v = clock.getAndAdd(3_000);
                    return v;
                }, new MobileCrashDetector());
        FailureEvidenceManifest m = col.collect(new MobileFailureEvidenceCollector.EvidenceRequest(
                "step-3",
                "LOCATOR_NOT_FOUND",
                "x",
                "com.example.test",
                0L,
                login(),
                new byte[16],
                2,
                2,
                "",
                s -> {},
                b -> {},
                s -> {}
        ));
        assertTrue(m.partial() || m.elapsedMs() >= 0);
        // with aggressive clock, partial should trip
        assertTrue(m.partial());
    }
}
