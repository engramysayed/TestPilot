package delivery.mobile.runner.settle;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotParser;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.*;

public class MobileSettleServiceTest {
    private final MobileSnapshotParser parser = new MobileSnapshotParser();

    private MobileSnapshot xml(String body) {
        MobileSnapshotMeta meta = new MobileSnapshotMeta(1, 1L, "com.example.test", ".A",
                "NATIVE", 0, 1080, 1920, "en", MobileSnapshotInvalidation.NONE);
        return parser.parse("<hierarchy>" + body + "</hierarchy>", meta);
    }

    @Test
    public void deadButtonSettleRecordsNoChangeAndToastWindow() {
        AtomicLong clock = new AtomicLong(0);
        MobileSettleService svc = MobileSettleService.forTests(clock::get);
        MobileSnapshot pre = xml("<node class=\"android.widget.Button\" text=\"Go\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        List<MobileSnapshot> seq = new ArrayList<>();
        seq.add(pre);
        seq.add(pre);
        HierarchyProbe probe = () -> {
            clock.addAndGet(200);
            return seq.get(Math.min(seq.size() - 1, (int) (clock.get() / 200)));
        };
        SettleResult r = svc.settle(SettleActionKind.TAP, pre, probe, null, null);
        assertEquals(r.outcome(), ActionOutcome.NO_CHANGE);
        assertFalse(r.toastWindowSnapshots().isEmpty());
    }

    @Test
    public void hierarchyChangeClassifiesChanged() {
        AtomicLong clock = new AtomicLong(0);
        MobileSettleService svc = MobileSettleService.forTests(clock::get);
        MobileSnapshot pre = xml("<node class=\"android.widget.Button\" text=\"A\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        MobileSnapshot post = xml("<node class=\"android.widget.Button\" text=\"B\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        List<MobileSnapshot> seq = List.of(post, post);
        var it = seq.iterator();
        HierarchyProbe probe = () -> {
            clock.addAndGet(200);
            return it.hasNext() ? it.next() : post;
        };
        SettleResult r = svc.settle(SettleActionKind.TAP, pre, probe, null, null);
        assertEquals(r.outcome(), ActionOutcome.CHANGED);
    }

    @Test
    public void flutterCanvasOnlyChangeIsChangedVisual() {
        AtomicLong clock = new AtomicLong(0);
        MobileSettleService svc = new MobileSettleService(
                SettleAfterTable.defaults(),
                new ActionOutcomeClassifier(new ScreenshotDiffThreshold(0.01)),
                200L, clock::get, false);
        MobileSnapshot same = xml("<node class=\"android.view.View\" content-desc=\"card\" clickable=\"true\" enabled=\"true\" bounds=\"[0,0][100,100]\" package=\"com.example.flutter\"/>");
        byte[] before = new byte[100];
        byte[] after = new byte[100];
        after[50] = 7;
        after[51] = 7;
        after[52] = 7;
        after[53] = 7; // one pixel RGBA changed → 1/25 = 0.04 > 0.01
        HierarchyProbe probe = () -> {
            clock.addAndGet(200);
            return same;
        };
        SettleResult r = svc.settle(SettleActionKind.TAP, same, probe, () -> after, before);
        assertEquals(r.outcome(), ActionOutcome.CHANGED_VISUAL);
    }

    @Test
    public void secretTypeSkipsChangeWait() {
        AtomicLong clock = new AtomicLong(0);
        MobileSettleService svc = MobileSettleService.forTests(clock::get);
        MobileSnapshot pre = xml("<node class=\"android.widget.EditText\" password=\"true\" text=\"x\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>");
        SettleResult r = svc.settle(SettleActionKind.TYPE_SECRET, pre, () -> pre, null, null);
        assertEquals(r.elapsedMs(), 0L);
        assertEquals(r.outcome(), ActionOutcome.NO_CHANGE);
    }
}
