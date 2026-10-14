package delivery.hunt;

import delivery.guard.LoopGuard;
import delivery.settle.SettlePolicy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** P0: map hunt action-log rows onto {@link LoopGuard.Step} (sideEffects + action signature). */
public final class HuntLoopGuardSupport {
    private HuntLoopGuardSupport() {}

    public static SettlePolicy.SideEffects sideEffectsOf(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "navigate", "back", "forward", "refresh", "wait",
                 "assert_visible", "assert_text", "restart_browser" -> SettlePolicy.SideEffects.NONE;
            case "clear" -> SettlePolicy.SideEffects.NONE;
            case "type", "click", "execute_js" -> SettlePolicy.SideEffects.UNKNOWN;
            default -> SettlePolicy.SideEffects.UNKNOWN;
        };
    }

    public static String actionKey(Map<String, Object> row) {
        if (row == null) return "unknown";
        String type = str(row.get("type"));
        String loc = str(row.get("locator"));
        if (loc.isBlank()) loc = str(row.get("url"));
        if (loc.isBlank()) loc = str(row.get("text"));
        // never include typed values (may be secrets)
        return type + " " + loc;
    }

    public static boolean ok(Map<String, Object> row) {
        String s = str(row == null ? null : row.get("status")).toLowerCase(Locale.ROOT);
        return "ok".equals(s) || "pass".equals(s) || "passed".equals(s);
    }

    /** Record every executed action; returns the last verdict (sticky STOP wins). */
    public static LoopGuard.Verdict recordAll(LoopGuard guard, List<Map<String, Object>> actionLog) {
        LoopGuard.Verdict last = new LoopGuard.Verdict(LoopGuard.Level.CONTINUE, null, "");
        if (guard == null || actionLog == null) return last;
        for (Map<String, Object> row : actionLog) {
            last = guard.record(new LoopGuard.Step(
                    null, actionKey(row), ok(row), 0, 0.0, sideEffectsOf(str(row.get("type")))));
            if (last.stop()) return last;
        }
        return last;
    }

    public static String stopReason(LoopGuard.Verdict v) {
        if (v == null || !v.stop() || v.terminal() == null) return "LOOP_GUARD";
        return "LOOP_GUARD_" + v.terminal().name();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
