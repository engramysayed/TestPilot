package delivery.mobile.runner.action;

import delivery.mobile.runner.settle.MobileSettleService;
import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotParser;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public class MobileGestureActionsTest {
    private final MobileSnapshotParser parser = new MobileSnapshotParser();

    private MobileSnapshot snap(String inner, int w, int h) {
        return parser.parse("<hierarchy>" + inner + "</hierarchy>",
                new MobileSnapshotMeta(1, 1L, "com.example.test", ".A", "NATIVE", 0, w, h, "en",
                        MobileSnapshotInvalidation.NONE));
    }

    private int buttonIndex(MobileSnapshot s) {
        return s.nodes().stream().filter(n -> n.className().contains("Button")).findFirst().orElseThrow().index();
    }

    @Test
    public void longPress_executes() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap(
                "<node class=\"android.widget.Button\" text=\"Hold\" clickable=\"true\" long-clickable=\"true\" enabled=\"true\" bounds=\"[0,0][20,20]\" package=\"com.example.test\"/>",
                1080, 1920);
        FakeMobileDriver d = new FakeMobileDriver(() -> { clock.addAndGet(50); return s; });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.longPress(buttonIndex(s), false, false, true);
        Assert.assertTrue(d.actions().stream().anyMatch(a -> a.startsWith("LONG_PRESS")));
        Assert.assertNotEquals(r.status(), ActionResult.Status.TRANSPORT_FAILED);
    }

    @Test
    public void scrollUntil_maxSwipesLimit_tuningMessage() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap(
                "<node class=\"android.widget.TextView\" text=\"far\" bounds=\"[0,5000][100,5100]\" package=\"com.example.test\"/>",
                1080, 1920);
        // mutate fingerprint each swipe so we hit maxSwipes not noProgress
        AtomicInteger gen = new AtomicInteger();
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(10);
            int g = gen.getAndIncrement();
            return snap("<node class=\"android.widget.TextView\" text=\"far" + g
                    + "\" bounds=\"[0,5000][100,5100]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        MobileBounds viewport = new MobileBounds(0, 0, 1080, 1920);
        AtomicReference<MobileBounds> target = new AtomicReference<>(new MobileBounds(0, 5000, 100, 5100));
        ScrollUntilRequest req = new ScrollUntilRequest(ScrollDirection.DOWN, 5, 2, 50, false, 30_000);
        ActionResult r = ex.scrollUntil(req, target::get, viewport, true);
        Assert.assertEquals(r.status(), ActionResult.Status.SCROLL_LIMIT);
        Assert.assertTrue(r.detail().contains("maxSwipes=5"), r.detail());
        Assert.assertTrue(r.detail().contains("visibilityPercentage=50"), r.detail());
        Assert.assertTrue(r.detail().contains("direction=DOWN"), r.detail());
        Assert.assertEquals(d.swipeCount(), 5);
    }

    @Test
    public void scrollUntil_noProgressLimit() {
        AtomicLong clock = new AtomicLong(0);
        MobileSnapshot s = snap(
                "<node class=\"android.widget.TextView\" text=\"stuck\" bounds=\"[0,5000][10,5010]\" package=\"com.example.test\"/>",
                1080, 1920);
        FakeMobileDriver d = new FakeMobileDriver(() -> { clock.addAndGet(5); return s; });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.scrollUntil(
                ScrollUntilRequest.defaults(ScrollDirection.UP),
                () -> new MobileBounds(0, 5000, 10, 5010),
                new MobileBounds(0, 0, 1080, 1920),
                true);
        Assert.assertEquals(r.status(), ActionResult.Status.SCROLL_LIMIT);
        Assert.assertTrue(r.detail().contains("noProgress"), r.detail());
        Assert.assertTrue(d.swipeCount() <= 5);
    }

    @Test
    public void scrollUntil_partiallyVisibleBelowThreshold_notFound() {
        AtomicLong clock = new AtomicLong(0);
        // target only ~25% visible in viewport → below default 50%
        MobileBounds viewport = new MobileBounds(0, 0, 100, 100);
        MobileBounds partial = new MobileBounds(0, 75, 100, 175); // 25px of 100 height inside
        Assert.assertEquals(VisibilityPercent.of(partial, viewport), 25);
        MobileSnapshot s = snap(
                "<node class=\"android.widget.TextView\" text=\"partial\" bounds=\"[0,75][100,175]\" package=\"com.example.test\"/>",
                100, 100);
        AtomicInteger gen = new AtomicInteger();
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            int g = gen.incrementAndGet();
            return snap("<node class=\"android.widget.TextView\" text=\"p" + g
                    + "\" bounds=\"[0,75][100,175]\" package=\"com.example.test\"/>", 100, 100);
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.scrollUntil(
                new ScrollUntilRequest(ScrollDirection.DOWN, 3, 5, 50, false, 30_000),
                () -> partial,
                viewport,
                true);
        Assert.assertEquals(r.status(), ActionResult.Status.SCROLL_LIMIT);
        Assert.assertFalse(r.passEvidence());
    }

    @Test
    public void scrollUntil_targetMeetsVisibility_ok() {
        AtomicLong clock = new AtomicLong(0);
        MobileBounds viewport = new MobileBounds(0, 0, 100, 100);
        MobileBounds full = new MobileBounds(10, 10, 90, 90);
        Assert.assertTrue(VisibilityPercent.of(full, viewport) >= 50);
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            return snap("<node class=\"android.widget.TextView\" text=\"here\" bounds=\"[10,10][90,90]\" package=\"com.example.test\"/>",
                    100, 100);
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.scrollUntil(
                ScrollUntilRequest.defaults(ScrollDirection.DOWN),
                () -> full,
                viewport,
                true);
        Assert.assertEquals(r.status(), ActionResult.Status.OK);
        Assert.assertEquals(d.swipeCount(), 0);
    }

    @Test
    public void swipe_rotationGeometryChanged() {
        AtomicLong clock = new AtomicLong(0);
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            return snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        d.setOrientation("LANDSCAPE");
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.swipe(NormalizedSwipe.forDirection(ScrollDirection.LEFT, 0.4), "PORTRAIT", true);
        Assert.assertEquals(r.status(), ActionResult.Status.GEOMETRY_CHANGED);
        Assert.assertEquals(d.swipeCount(), 0);
    }

    @Test
    public void swipe_boundedNormalizedResolved() {
        AtomicLong clock = new AtomicLong(0);
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            return snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        d.setWindow(1000, 2000);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        NormalizedSwipe swipe = new NormalizedSwipe(-1, 2, 0.5, 0.5, 200); // clamps
        ActionResult r = ex.swipe(swipe, "PORTRAIT", true);
        Assert.assertNotEquals(r.status(), ActionResult.Status.GEOMETRY_CHANGED);
        Assert.assertEquals(d.swipeCount(), 1);
        Assert.assertTrue(d.actions().stream().anyMatch(a -> a.startsWith("SWIPE:")));
    }

    @Test
    public void hideKeyboard_backNotSentWhenHidden() {
        AtomicLong clock = new AtomicLong(0);
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            return snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        d.setKeyboardShown(false);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.hideKeyboard();
        Assert.assertEquals(r.status(), ActionResult.Status.OK);
        Assert.assertFalse(d.actions().contains("KEY:" + MobileDriverPort.KEYCODE_BACK));
        Assert.assertFalse(d.actions().contains("KEY:" + MobileDriverPort.KEYCODE_ESC));
        Assert.assertTrue(r.detail().contains("already hidden"));
    }

    @Test
    public void hideKeyboard_escAloneDismisses() {
        AtomicLong clock = new AtomicLong(0);
        FakeMobileDriver d = new FakeMobileDriver(() ->
            snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920));
        d.setKeyboardShown(true);
        d.setEscDismisses(true);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(() -> clock.addAndGet(50)), () -> clock.addAndGet(50));
        ActionResult r = ex.hideKeyboard();
        Assert.assertEquals(r.status(), ActionResult.Status.OK);
        Assert.assertTrue(d.actions().contains("KEY:" + MobileDriverPort.KEYCODE_ESC));
        Assert.assertFalse(d.actions().contains("KEY:" + MobileDriverPort.KEYCODE_BACK));
    }

    @Test
    public void hideKeyboard_backFallback_andObstructingFails() {
        AtomicLong clock = new AtomicLong(0);
        FakeMobileDriver d = new FakeMobileDriver(() ->
            snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920));
        d.setKeyboardShown(true);
        d.setEscDismisses(false);
        d.setBackDismisses(true);
        // ESC fails poll (keyboard still shown); BACK then dismisses
        LongSupplier slow = () -> clock.addAndGet(600);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(slow), slow);
        ActionResult r = ex.hideKeyboard();
        Assert.assertEquals(r.status(), ActionResult.Status.OK);
        Assert.assertTrue(d.actions().contains("KEY:" + MobileDriverPort.KEYCODE_ESC));
        Assert.assertTrue(d.actions().contains("KEY:" + MobileDriverPort.KEYCODE_BACK));

        clock.set(0);
        FakeMobileDriver d2 = new FakeMobileDriver(() ->
            snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920));
        d2.setKeyboardShown(true);
        d2.setEscDismisses(false);
        d2.setBackDismisses(false);
        ActionResult fail = new MobileActionExecutor(d2, MobileSettleService.forTests(slow), slow).hideKeyboard();
        Assert.assertEquals(fail.status(), ActionResult.Status.KEYBOARD_NOT_DISMISSED);
    }

    @Test
    public void scrollUntil_obstructingKeyboard_surfacesError() {
        AtomicLong clock = new AtomicLong(0);
        AtomicInteger gen = new AtomicInteger();
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(1100);
            int g = gen.incrementAndGet();
            return snap("<node class=\"android.widget.TextView\" text=\"x" + g
                    + "\" bounds=\"[0,5000][10,5010]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        d.setKeyboardShown(true);
        d.setEscDismisses(false);
        d.setBackDismisses(false);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.scrollUntil(
                new ScrollUntilRequest(ScrollDirection.DOWN, 5, 5, 50, false, 30_000),
                () -> new MobileBounds(0, 5000, 10, 5010),
                new MobileBounds(0, 0, 1080, 1920),
                true);
        Assert.assertEquals(r.status(), ActionResult.Status.KEYBOARD_NOT_DISMISSED);
    }

    @Test
    public void scrollUntil_actionCancellation() {
        AtomicLong clock = new AtomicLong(0);
        AtomicInteger gen = new AtomicInteger();
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            int g = gen.incrementAndGet();
            return snap("<node class=\"android.widget.TextView\" text=\"x" + g
                    + "\" bounds=\"[0,5000][10,5010]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        d.setCancelled(true);
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        ActionResult r = ex.scrollUntil(
                ScrollUntilRequest.defaults(ScrollDirection.DOWN),
                () -> new MobileBounds(0, 5000, 10, 5010),
                new MobileBounds(0, 0, 1080, 1920),
                true);
        Assert.assertEquals(r.status(), ActionResult.Status.CANCELLED);
        Assert.assertEquals(d.swipeCount(), 0);
    }

    @Test
    public void pressBack_andLaunchRestart_withoutDataWipe() {
        AtomicLong clock = new AtomicLong(0);
        FakeMobileDriver d = new FakeMobileDriver(() -> {
            clock.addAndGet(5);
            return snap("<node class=\"android.view.View\" bounds=\"[0,0][10,10]\" package=\"com.example.test\"/>", 1080, 1920);
        });
        MobileActionExecutor ex = new MobileActionExecutor(d, MobileSettleService.forTests(clock::get), clock::get);
        Assert.assertNotEquals(ex.pressBack(true).status(), ActionResult.Status.TRANSPORT_FAILED);
        Assert.assertTrue(d.actions().contains("BACK"));
        Assert.assertEquals(ex.launch("com.example.test", ".Main", true).status(), ActionResult.Status.OK);
        ActionResult rr = ex.restart("com.example.test", true);
        Assert.assertEquals(rr.status(), ActionResult.Status.OK);
        Assert.assertEquals(d.restartCount(), 1);
        Assert.assertFalse(d.lastRestartClearedData());
        Assert.assertFalse(d.clearDataAttempted());
        Assert.assertTrue(rr.detail().contains("clearData=false"));
        Assert.assertTrue(d.actions().contains("RESTART:com.example.test:clearData=false"));
    }

    @Test
    public void normalizedSwipe_clampsOutOfRange() {
        NormalizedSwipe s = new NormalizedSwipe(1.5, -0.2, 0.3, 0.9, 0);
        Assert.assertEquals(s.startX(), 1.0);
        Assert.assertEquals(s.startY(), 0.0);
        Assert.assertTrue(s.durationMs() >= 1);
        int[] px = s.toDevicePx(100, 200);
        Assert.assertEquals(px[0], 99);
        Assert.assertEquals(px[1], 0);
    }
}
