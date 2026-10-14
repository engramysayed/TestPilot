package delivery.hunt;

import delivery.guard.LoopGuard;
import delivery.settle.SettlePolicy;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.*;

public class HuntLoopGuardSupportTest {
    private static Map<String, Object> row(String type, String locator, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("locator", locator);
        m.put("status", status);
        return m;
    }

    @Test public void navigateIsNoneClickIsUnknown() {
        assertEquals(HuntLoopGuardSupport.sideEffectsOf("navigate"), SettlePolicy.SideEffects.NONE);
        assertEquals(HuntLoopGuardSupport.sideEffectsOf("click"), SettlePolicy.SideEffects.UNKNOWN);
    }

    @Test public void actionKeyOmitsTypedValues() {
        Map<String, Object> m = row("type", "#password", "ok");
        m.put("value", "hunter2-secret");
        assertEquals(HuntLoopGuardSupport.actionKey(m), "type #password");
        assertFalse(HuntLoopGuardSupport.actionKey(m).contains("hunter2"));
    }

    @Test public void unsafeRetryStopsRepeatedFailedClick() {
        AtomicLong now = new AtomicLong(1000);
        LoopGuard g = new LoopGuard(LoopGuard.Loop.BUG_HUNTER, LoopGuard.builtin(LoopGuard.Loop.BUG_HUNTER), now::get);
        List<Map<String, Object>> fail = List.of(row("click", "#pay", "fail"));
        assertFalse(HuntLoopGuardSupport.recordAll(g, fail).stop());
        now.addAndGet(10);
        // planner synthetic + same fail again
        g.record(new LoopGuard.Step(null, "planner:continue", true, 1, 1, SettlePolicy.SideEffects.NONE));
        var v = HuntLoopGuardSupport.recordAll(g, fail);
        assertTrue(v.stop(), v.toString());
        assertEquals(v.terminal(), LoopGuard.Terminal.UNSAFE_RETRY);
        assertEquals(HuntLoopGuardSupport.stopReason(v), "LOOP_GUARD_UNSAFE_RETRY");
    }

    @Test public void repeatedActionEventuallyStopsHunter() {
        AtomicLong now = new AtomicLong(1000);
        LoopGuard g = new LoopGuard(LoopGuard.Loop.BUG_HUNTER, LoopGuard.builtin(LoopGuard.Loop.BUG_HUNTER), now::get);
        LoopGuard.Verdict v = null;
        // none sideEffects so UNSAFE_RETRY does not fire; repeat threshold stopRepeatAction=5
        for (int i = 0; i < 10; i++) {
            now.addAndGet(5);
            v = g.record(new LoopGuard.Step(null, "click #x", false, 0, 0, SettlePolicy.SideEffects.NONE));
            if (v.stop()) break;
        }
        assertNotNull(v);
        assertTrue(v.stop());
        assertEquals(v.terminal(), LoopGuard.Terminal.REPEATED_ACTION);
    }
}
