package project.runtime.settle;

import java.util.EnumMap;
import java.util.Map;

/**
 * Single SETTLE_AFTER table shared with AE worker (arch §6).
 * defaults() must stay byte-semantic-identical to
 * delivery.mobile.runner.settle.SettleAfterTable.defaults().
 * No blocking sleep — callers poll via {@link SettleProbe}.
 */
public final class SettleAfterTable {
    private final Map<SettleActionKind, SettleRule> rules;

    public SettleAfterTable(Map<SettleActionKind, SettleRule> rules) {
        this.rules = Map.copyOf(new EnumMap<>(rules));
    }

    public static SettleAfterTable defaults() {
        EnumMap<SettleActionKind, SettleRule> m = new EnumMap<>(SettleActionKind.class);
        m.put(SettleActionKind.TAP, SettleRule.heldStill(2000));
        m.put(SettleActionKind.LONG_PRESS, SettleRule.heldStill(2000));
        m.put(SettleActionKind.BACK, SettleRule.heldStill(2000));
        m.put(SettleActionKind.TYPE, SettleRule.afterChange(2000));
        m.put(SettleActionKind.TYPE_SECRET, SettleRule.none());
        m.put(SettleActionKind.CLEAR, SettleRule.afterChange(2000));
        m.put(SettleActionKind.SCROLL_UNTIL, SettleRule.heldStill(500));
        m.put(SettleActionKind.SWIPE, SettleRule.heldStill(500));
        m.put(SettleActionKind.OTHER, SettleRule.heldStill(2000));
        return new SettleAfterTable(m);
    }

    public SettleRule ruleFor(SettleActionKind kind) {
        SettleRule r = rules.get(kind);
        if (r == null) {
            return SettleRule.heldStill(2000);
        }
        return r;
    }

    public SettleRule ruleFor(String actionKind) {
        return ruleFor(SettleActionKind.parse(actionKind));
    }

    /** Compatibility helper for older scaffold call sites. */
    public static int capMs(String actionKind) {
        return (int) defaults().ruleFor(actionKind).capMs();
    }

    public Map<SettleActionKind, SettleRule> asMap() {
        return rules;
    }
}
