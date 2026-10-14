package project.runtime.settle;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.EnumMap;
import java.util.Map;

/** Asserts customer SettleAfterTable.defaults() matches AE M2-04 semantics. */
public class SettleAfterTableParityTest {

    @Test
    public void defaultsMatchAeCapsAndModes() {
        SettleAfterTable table = SettleAfterTable.defaults();
        assertRule(table, SettleActionKind.TAP, SettleRule.SettleMode.HELD_STILL, 2000);
        assertRule(table, SettleActionKind.LONG_PRESS, SettleRule.SettleMode.HELD_STILL, 2000);
        assertRule(table, SettleActionKind.BACK, SettleRule.SettleMode.HELD_STILL, 2000);
        assertRule(table, SettleActionKind.TYPE, SettleRule.SettleMode.AFTER_CHANGE_THEN_HELD, 2000);
        assertRule(table, SettleActionKind.TYPE_SECRET, SettleRule.SettleMode.NONE, 0);
        assertRule(table, SettleActionKind.CLEAR, SettleRule.SettleMode.AFTER_CHANGE_THEN_HELD, 2000);
        assertRule(table, SettleActionKind.SCROLL_UNTIL, SettleRule.SettleMode.HELD_STILL, 500);
        assertRule(table, SettleActionKind.SWIPE, SettleRule.SettleMode.HELD_STILL, 500);
        assertRule(table, SettleActionKind.OTHER, SettleRule.SettleMode.HELD_STILL, 2000);
    }

    @Test
    public void capMsHelperMatchesDefaults() {
        Assert.assertEquals(SettleAfterTable.capMs("TAP"), 2000);
        Assert.assertEquals(SettleAfterTable.capMs("TYPE_SECRET"), 0);
        Assert.assertEquals(SettleAfterTable.capMs("SWIPE"), 500);
    }

    @Test
    public void classifierHierarchyWinsOverScreenshot() {
        ActionOutcomeClassifier c = new ActionOutcomeClassifier();
        Assert.assertEquals(c.classify(true, 0.0), ActionOutcome.CHANGED);
        Assert.assertEquals(c.classify(false, 0.01), ActionOutcome.CHANGED_VISUAL);
        Assert.assertEquals(c.classify(false, 0.0001), ActionOutcome.NO_CHANGE);
        Assert.assertEquals(c.classify(false, null), ActionOutcome.NO_CHANGE);
    }

    @Test
    public void settleExecutorShortCircuitNoSleep() {
        SettleExecutor exec = new SettleExecutor();
        ActionOutcome out = exec.settle(
                SettleActionKind.TYPE_SECRET,
                "pre",
                SettleProbe.immediate("pre"));
        Assert.assertEquals(out, ActionOutcome.NO_CHANGE);

        ActionOutcome changed = exec.settle(
                SettleActionKind.TAP,
                "pre",
                SettleProbe.immediate("post"));
        Assert.assertEquals(changed, ActionOutcome.CHANGED);
    }

    @Test
    public void defaultsMapIsComplete() {
        Map<SettleActionKind, SettleRule> map = SettleAfterTable.defaults().asMap();
        Assert.assertEquals(map.size(), SettleActionKind.values().length);
        EnumMap<SettleActionKind, SettleRule> copy = new EnumMap<>(map);
        Assert.assertEquals(copy.size(), 9);
    }

    private static void assertRule(
            SettleAfterTable table, SettleActionKind kind, SettleRule.SettleMode mode, long cap) {
        SettleRule r = table.ruleFor(kind);
        Assert.assertEquals(r.mode(), mode, kind.name());
        Assert.assertEquals(r.capMs(), cap, kind.name());
    }
}
