package delivery.mobile.runner.action;

import delivery.mobile.runner.settle.ActionOutcome;
import delivery.mobile.runner.settle.MobileSettleService;
import delivery.mobile.runner.settle.SettleActionKind;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotParser;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.*;

public class MobileActionExecutorTest {
    private final MobileSnapshotParser parser = new MobileSnapshotParser();

    private MobileSnapshot snap(String inner) {
        return parser.parse("<hierarchy>" + inner + "</hierarchy>",
                new MobileSnapshotMeta(1, 1L, "com.example.test", ".A", "NATIVE", 0, 1080, 1920, "en",
                        MobileSnapshotInvalidation.NONE));
    }

    private int buttonIndex(MobileSnapshot s) {
        return s.nodes().stream().filter(n -> n.className().contains("Button")).findFirst().orElseThrow().index();
    }

    private int editIndex(MobileSnapshot s) {
        return s.nodes().stream().filter(n -> n.className().contains("EditText")).findFirst().orElseThrow().index();
    }

    @Test
    public void deadButtonNoChangeNeverPass() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap("<node class=\"android.widget.Button\" text=\"Dead\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(200);
            return s;
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        ActionResult r = ex.tap(buttonIndex(s), false, false, true);
        assertEquals(r.status(), ActionResult.Status.ACTION_NO_EFFECT);
        assertEquals(r.outcome(), ActionOutcome.NO_CHANGE);
        assertFalse(r.passEvidence());
    }

    @Test
    public void transportFailureSurfaced() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap("<node class=\"android.widget.Button\" text=\"X\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        FakeMobileDriver d = new FakeMobileDriver(() -> s);
        d.setTransportFail(true);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        ActionResult r = ex.tap(buttonIndex(s), false, false, true);
        assertEquals(r.status(), ActionResult.Status.TRANSPORT_FAILED);
    }

    @Test
    public void staleRequerySurfaced() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap("<node class=\"android.widget.Button\" text=\"X\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        FakeMobileDriver d = new FakeMobileDriver(() -> s);
        d.setStaleOnTap(true);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        assertEquals(ex.tap(buttonIndex(s), false, false, true).status(), ActionResult.Status.STALE);
    }

    @Test
    public void hiddenDisabledControl() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap("<node class=\"android.widget.Button\" text=\"X\" clickable=\"false\" enabled=\"false\" displayed=\"false\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        // parser may default displayed true if attr missing — set enabled false clickable false
        FakeMobileDriver d = new FakeMobileDriver(() -> s);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        ActionResult r = ex.tap(buttonIndex(s), false, false, true);
        assertEquals(r.status(), ActionResult.Status.HIDDEN_OR_DISABLED);
    }

    @Test
    public void successfulCommandFailedBusinessAssertion() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot pre = snap("<node class=\"android.widget.Button\" text=\"A\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        MobileSnapshot post = snap("<node class=\"android.widget.Button\" text=\"B\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        AtomicLong n = new AtomicLong();
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(200);
            return n.getAndIncrement() == 0 ? pre : post;
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        ActionResult r = ex.tap(buttonIndex(pre), false, false, false);
        assertEquals(r.status(), ActionResult.Status.ASSERTION_FAILED);
        assertFalse(r.passEvidence());
    }

    @Test
    public void noUncertainActionReplayWhenRetryRejected() {
        try {
            RetryIfNoChangePolicy.requireLegal(true, SettleActionKind.TYPE, true);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("rejected"));
        }
        assertEquals(
                RetryIfNoChangePolicy.evaluate(true, SettleActionKind.TYPE, false, ActionOutcome.NO_CHANGE, false),
                RetryIfNoChangePolicy.Decision.REJECT);
    }

    @Test
    public void arabicInputReadBackExactUsesUnicodePath() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap("<node class=\"android.widget.EditText\" text=\"\" clickable=\"true\" enabled=\"true\" focusable=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(200);
            return s;
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        String arabic = "مرحبا";
        assertFalse(TypeInputPolicy.adbInputTextAllowed(arabic));
        ActionResult r = ex.type(editIndex(s), arabic, false, true);
        assertTrue(d.typePathsUsed().contains("UNICODE"));
        assertFalse(d.typePathsUsed().contains("ADB"));
        assertNotEquals(r.status(), ActionResult.Status.TYPE_MISMATCH);
        assertEquals(TypeInputPolicy.nfc(d.typed()), TypeInputPolicy.nfc(arabic));
    }

    @Test
    public void secretTypeSkipsReadBack() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap("<node class=\"android.widget.EditText\" password=\"true\" text=\"\" clickable=\"true\" enabled=\"true\" focusable=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        FakeMobileDriver d = new FakeMobileDriver(() -> s);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get));
        ActionResult r = ex.type(editIndex(s), "secret-value", true, true);
        assertEquals(r.settle().elapsedMs(), 0L);
        // fake still stores typed but policy skipped read-back mismatch path
        assertNotEquals(r.status(), ActionResult.Status.TYPE_MISMATCH);
    }

    @Test
    public void teardownCompletesAfterCaseDeadlineExhaustion() {
        long caseDeadline = 1_000L;
        long now = 1_500L; // case already exhausted
        TeardownDeadline td = new TeardownDeadline(30_000L, caseDeadline, now);
        assertTrue(td.caseExhaustedButTeardownOpen(now));
        assertFalse(td.teardownExceeded(now + 10_000));
        assertTrue(td.teardownExceeded(now + 30_000));
    }
}
