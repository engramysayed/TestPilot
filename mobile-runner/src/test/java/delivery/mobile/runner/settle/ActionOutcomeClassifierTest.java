package delivery.mobile.runner.settle;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ActionOutcomeClassifierTest {
    private final ActionOutcomeClassifier c = new ActionOutcomeClassifier(new ScreenshotDiffThreshold(0.01));

    @Test
    public void hierarchyChangeIsChanged() {
        assertEquals(c.classify(true, 0.0), ActionOutcome.CHANGED);
    }

    @Test
    public void canvasOnlyIsChangedVisual() {
        assertEquals(c.classify(false, 0.05), ActionOutcome.CHANGED_VISUAL);
    }

    @Test
    public void deadButtonIsNoChange() {
        assertEquals(c.classify(false, 0.0), ActionOutcome.NO_CHANGE);
        assertEquals(c.classify(false, null), ActionOutcome.NO_CHANGE);
    }

    @Test
    public void settleAfterTableDefaults() {
        SettleAfterTable t = SettleAfterTable.defaults();
        assertEquals(t.ruleFor(SettleActionKind.TAP).capMs(), 2000L);
        assertEquals(t.ruleFor(SettleActionKind.TYPE_SECRET).mode(), SettleRule.SettleMode.NONE);
        assertEquals(t.ruleFor(SettleActionKind.SWIPE).capMs(), 500L);
    }
}
