package delivery.routing;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ObservationBudgetTest {
    @Test public void huntAndHealBudgetsPositiveAndClip() {
        assertTrue(ObservationBudget.forIntent(ModelRouter.Intent.HUNT_PLANNING) >= 1024);
        assertTrue(ObservationBudget.forIntent(ModelRouter.Intent.HEAL) >= 1024);
        assertEquals(ObservationBudget.clip("abcdef", 3), "abc");
    }

    @Test
    public void proveBudgetUsesBindIntentAndIsPositive() {
        int prove = ObservationBudget.forProve();
        int bind = ObservationBudget.forIntent(ModelRouter.Intent.BIND);
        assertEquals(prove, bind);
        assertTrue(prove >= 1024, "prove budget too small: " + prove);
        // Must not be the old hard-coded 80k when routing is available — allow either routed or fallback
        assertTrue(prove <= 100_000, "prove budget unexpectedly huge: " + prove);
    }

    @Test
    public void provePhaseHelperMatchesBudget() {
        assertEquals(delivery.job.ProvePhase.proveHtmlBudget(), ObservationBudget.forProve());
    }
}
