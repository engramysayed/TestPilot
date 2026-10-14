package delivery.guard;

import delivery.effect.EffectSignature;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.*;

public class LoopGuardTest {
    private final AtomicLong now = new AtomicLong(1_000);

    static EffectSignature st(String page) {
        return new EffectSignature("http://x/" + page, "", "pm-" + page, "d-" + page, 10, "");
    }

    private LoopGuard guard(LoopGuard.Loop l) {
        return new LoopGuard(l, LoopGuard.builtin(l), now::get);
    }

    private static LoopGuard.Step step(String page, String action, boolean ok) {
        return new LoopGuard.Step(st(page), action, ok, 1, 1, delivery.settle.SettlePolicy.SideEffects.NONE);
    }

    @Test public void builtinTableLoadsForEveryLoop() {
        for (LoopGuard.Loop l : LoopGuard.Loop.values()) assertTrue(LoopGuard.builtin(l).maxAttempts() > 0);
        assertEquals(LoopGuard.builtin(LoopGuard.Loop.HEAL).stopRepeatAction(), 3);
    }

    @Test public void repeatedActionWarnsThenStops() {
        LoopGuard g = guard(LoopGuard.Loop.HEAL);
        assertEquals(g.record(step("a", "click #x", false)).level(), LoopGuard.Level.CONTINUE);
        now.addAndGet(10);
        assertEquals(g.record(step("a", "click #x", false)).level(), LoopGuard.Level.WARN);
        LoopGuard.Verdict v = g.record(step("a", "click #x", false));
        assertTrue(v.stop());
        assertEquals(v.terminal(), LoopGuard.Terminal.REPEATED_ACTION);
        assertEquals(g.record(step("b", "other", true)), v, "terminal is sticky");
        assertTrue(g.beforeIteration().stop());
    }

    @Test public void sameActionOnDifferentStatesIsNotARepeat() {
        LoopGuard g = guard(LoopGuard.Loop.BUG_HUNTER);
        for (int i = 0; i < 10; i++) assertFalse(g.record(step("p" + i, "scroll down", true)).stop());
    }

    @Test public void periodTwoCycleDetected() {
        LoopGuard g = guard(LoopGuard.Loop.BUG_HUNTER);
        LoopGuard.Verdict v = null;
        String[][] seq = {{"list", "open item"}, {"item", "back"}};
        int i = 0;
        do { String[] s = seq[i++ % 2]; v = g.record(step(s[0], s[1], true)); } while (!v.stop() && i < 20);
        assertEquals(v.terminal(), LoopGuard.Terminal.CYCLE, v.toString());
        assertEquals(i, 6, "3 full repetitions of the 2-cycle");
    }

    @Test public void periodThreeCycleWarnsFirst() {
        LoopGuard g = guard(LoopGuard.Loop.BUG_HUNTER);
        String[] pages = {"a", "b", "c"};
        boolean warned = false;
        LoopGuard.Verdict v;
        int i = 0;
        do { v = g.record(step(pages[i % 3], "go" + (i % 3), true)); warned |= v.level() == LoopGuard.Level.WARN; i++; } while (!v.stop());
        assertTrue(warned);
        assertEquals(v.terminal(), LoopGuard.Terminal.CYCLE);
        assertEquals(i, 9);
    }

    @Test public void failureStreak() {
        LoopGuard g = guard(LoopGuard.Loop.HEAL);
        LoopGuard.Verdict v = null;
        for (int i = 0; i < 4; i++) v = g.record(step("s" + i, "try" + i, false));
        assertEquals(v.terminal(), LoopGuard.Terminal.FAILURE_STREAK);
    }

    @Test public void successResetsStreak() {
        LoopGuard g = guard(LoopGuard.Loop.HEAL);
        for (int i = 0; i < 3; i++) g.record(step("s" + i, "try" + i, false));
        assertFalse(g.record(step("ok", "fix", true)).stop());
        assertFalse(g.record(step("s9", "try9", false)).stop());
    }

    @Test public void noProgressUsesA7StateSignature() {
        LoopGuard g = guard(LoopGuard.Loop.PRECISION);
        LoopGuard.Verdict v = null;
        for (int i = 0; i < 4 && (v == null || !v.stop()); i++) v = g.record(step("same", "a" + i, true));
        assertEquals(v.terminal(), LoopGuard.Terminal.NO_PROGRESS, v.toString());
    }

    @Test public void budgetsAndTimeCap() {
        LoopGuard g = guard(LoopGuard.Loop.PRECISION); // maxModelCalls 4
        LoopGuard.Verdict v = null;
        for (int i = 0; i < 2; i++) v = g.record(new LoopGuard.Step(st("p" + i), "a" + i, true, 2, 0));
        assertEquals(v.terminal(), LoopGuard.Terminal.MODEL_CALL_BUDGET);

        LoopGuard c = guard(LoopGuard.Loop.HEAL); // maxCostUnits 40
        assertEquals(c.record(new LoopGuard.Step(st("x"), "big", true, 1, 41)).terminal(), LoopGuard.Terminal.COST_BUDGET);

        LoopGuard t = guard(LoopGuard.Loop.HEAL);
        now.addAndGet(120_001);
        assertEquals(t.beforeIteration().terminal(), LoopGuard.Terminal.TIME_CAP);

        LoopGuard a = guard(LoopGuard.Loop.HEAL); // maxAttempts 6
        for (int i = 0; i < 6; i++) v = a.record(new LoopGuard.Step(st("q" + i), "b" + i, i % 2 == 0, 0, 0));
        assertEquals(v.terminal(), LoopGuard.Terminal.MAX_ATTEMPTS);
    }

    @Test public void unknownStateNeverCountsAsNoProgress() {
        LoopGuard g = guard(LoopGuard.Loop.PRECISION);
        for (int i = 0; i < 3; i++) assertFalse(g.record(new LoopGuard.Step(null, "a" + i, true, 0, 0)).stop());
    }

    @org.testng.annotations.Test public void sideEffectsGateRetries() {
        var M = delivery.settle.SettlePolicy.SideEffects.MUTATING;
        LoopGuard g = LoopGuard.of(LoopGuard.Loop.HEAL);
        assertFalse(g.record(new LoopGuard.Step(st("form"), "click Pay", false, 1, 1, M)).stop());
        LoopGuard.Verdict v = g.record(new LoopGuard.Step(st("form"), "click Pay", false, 1, 1, M));
        assertEquals(v.terminal(), LoopGuard.Terminal.UNSAFE_RETRY);
        // unknown (absent) behaves like mutating
        LoopGuard u = LoopGuard.of(LoopGuard.Loop.HEAL);
        u.record(new LoopGuard.Step(st("f"), "click Go", false, 1, 1));
        assertEquals(u.record(new LoopGuard.Step(st("f"), "click Go", true, 1, 1)).terminal(), LoopGuard.Terminal.UNSAFE_RETRY);
        // a successful mutating step may legitimately repeat (add 2 items); a different state is a different step
        LoopGuard ok = LoopGuard.of(LoopGuard.Loop.BUG_HUNTER);
        assertFalse(ok.record(new LoopGuard.Step(st("cart"), "click Add", true, 0, 0, M)).stop());
        assertFalse(ok.record(new LoopGuard.Step(st("cart2"), "click Add", false, 0, 0, M)).stop());
        assertFalse(ok.record(new LoopGuard.Step(st("cart3"), "click Add", false, 0, 0, M)).stop());
        assertTrue(LoopGuard.mayRetry(delivery.settle.SettlePolicy.SideEffects.NONE));
        assertFalse(LoopGuard.mayRetry(null));
        assertEquals(LoopGuard.safeRetries(M, 2), 0);
        assertEquals(LoopGuard.safeRetries(delivery.settle.SettlePolicy.SideEffects.NONE, 2), 2);
    }
}
