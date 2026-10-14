package delivery.mobile.runner.device;

import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.*;

public class MobileCrashDetectorTest {
    private final MobileCrashDetector detector = new MobileCrashDetector();

    private static String resource(String name) throws Exception {
        try (InputStream in = MobileCrashDetectorTest.class.getClassLoader().getResourceAsStream("logcat/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void fatalExceptionForAppPackage() throws Exception {
        CrashDetectionResult r = detector.detect("com.example.test", 900L, resource("fatal-exception.txt"));
        assertTrue(r.appCrashed());
        assertEquals(r.kind(), "FATAL_EXCEPTION");
        assertFalse(HealVisionGate.allowHeal(r));
        assertFalse(HealVisionGate.allowVision(r));
    }

    @Test
    public void anrForAppPackage() throws Exception {
        CrashDetectionResult r = detector.detect("com.example.test", 1000L, resource("anr.txt"));
        assertTrue(r.appCrashed());
        assertEquals(r.kind(), "ANR");
    }

    @Test
    public void nativeSigsegv() throws Exception {
        CrashDetectionResult r = detector.detect("com.example.test", 2000L, resource("native-sigsegv.txt"));
        assertTrue(r.appCrashed());
        assertEquals(r.kind(), "NATIVE_SIGNAL");
    }

    @Test
    public void otherPackageIgnored() throws Exception {
        CrashDetectionResult r = detector.detect("com.example.test", 1000L, resource("other-package.txt"));
        assertFalse(r.appCrashed());
        assertTrue(HealVisionGate.allowHeal(r));
    }

    @Test
    public void preStepCrashIgnored() throws Exception {
        CrashDetectionResult r = detector.detect("com.example.test", 1000L, resource("pre-step-crash.txt"));
        assertFalse(r.appCrashed());
    }

    @Test
    public void oversizedLogBounded() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MobileCrashDetector.MAX_LINES + 100; i++) {
            sb.append("line ").append(i).append(" padding-").append("x".repeat(200)).append('\n');
        }
        sb.append("E AndroidRuntime: FATAL EXCEPTION: main\n");
        sb.append("E AndroidRuntime: Process: com.example.test, PID: 1\n");
        String bounded = MobileCrashDetector.bound(sb.toString());
        assertTrue(bounded.getBytes(StandardCharsets.UTF_8).length <= MobileCrashDetector.MAX_BYTES
                || bounded.split("\\R").length <= MobileCrashDetector.MAX_LINES);
        // detection on bounded buffer still works when crash is within bound window — if truncated away, no crash
        CrashDetectionResult r = detector.detect("com.example.test", 0L, bounded);
        // may or may not see crash depending on truncation; invariant is no throw + bounded input
        assertNotNull(r);
    }
}
