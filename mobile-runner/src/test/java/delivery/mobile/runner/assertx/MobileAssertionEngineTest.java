package delivery.mobile.runner.assertx;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class MobileAssertionEngineTest {
    private final MobileAssertionEngine eng = new MobileAssertionEngine();

    private static MobileSnapshotNode node(String text, boolean displayed) {
        return new MobileSnapshotNode(0, null, "android.widget.TextView", "com.example.test",
                "com.example.test:id/x", "", text, "", new MobileBounds(0, 0, 10, 10),
                true, displayed, false, false, false, false, false, false, false, false, "", List.of());
    }

    @Test
    public void falseExpectedFails() {
        AssertResult r = eng.assertExactText(node("A", true), "B");
        assertFalse(r.passed());
    }

    @Test
    public void exactIdBoundary() {
        assertTrue(eng.assertExactIdBoundary("KLA-1001", "KLA-1001").passed());
        assertFalse(eng.assertExactIdBoundary("KLA-10010", "KLA-1001").passed());
    }

    @Test
    public void allMatchAbsence() {
        assertFalse(eng.assertAbsent(List.of(node("x", true))).passed());
        assertTrue(eng.assertAbsent(List.of()).passed());
    }

    @Test
    public void setupCapturesRetainedAndCasesIsolated() {
        CaptureStore setup = new CaptureStore();
        setup.put("orderId", "ORD-1");
        CaptureStore caseA = setup.snapshot();
        CaptureStore caseB = setup.snapshot();
        caseA.put("orderId", "ORD-A");
        assertEquals(caseB.get("orderId").orElseThrow(), "ORD-1");
        assertEquals(setup.get("orderId").orElseThrow(), "ORD-1");
    }

    @Test
    public void missingAndAmbiguousCaptureFails() {
        CaptureStore store = new CaptureStore();
        assertEquals(eng.captureText(store, "v", List.of()).code(), "CAPTURE_MISSING");
        assertEquals(eng.captureText(store, "v", List.of(node("a", true), node("b", true))).code(), "CAPTURE_AMBIGUOUS");
        assertTrue(eng.captureText(store, "v", List.of(node("only", true))).passed());
        assertEquals(eng.compareCaptured(store, "missing", "x", "EXACT").code(), "COMPARE_MISSING");
    }

    @Test
    public void absentVsEmptyExplicit() {
        assertTrue(eng.absentVsEmpty(null, true).passed());
        assertFalse(eng.absentVsEmpty(node("", true), true).passed());
        assertTrue(eng.absentVsEmpty(node("", true), false).passed());
        assertFalse(eng.absentVsEmpty(node("x", true), false).passed());
    }
}
