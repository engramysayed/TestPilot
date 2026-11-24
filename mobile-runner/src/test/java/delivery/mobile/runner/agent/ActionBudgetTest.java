package delivery.mobile.runner.agent;

import delivery.mobile.runner.action.MobileDriverPort;
import delivery.mobile.runner.execute.CaseOutcome;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ActionBudgetTest {

    @Test
    public void theThirdActionIsRefused() {
        ActionBudget budget = new ActionBudget(2);
        assertTrue(budget.take());
        assertTrue(budget.take());
        assertFalse(budget.take());
        assertTrue(budget.exhausted());
    }

    @Test
    public void zeroStopsBeforeTheFirstStep() {
        MobileCaseRunner runner = new MobileCaseRunner(new SilentDriver(), "com.app", "", null, null);
        MobileCaseRunner.Outcome outcome = runner.run(new MobileCaseRunner.CaseInput(
                "Login", List.of("Tap \"Email\""), List.of(), List.of()), false, new ActionBudget(0));
        assertEquals(outcome.outcome(), CaseOutcome.FAILED);
        assertEquals(outcome.failureCode(), "STEP_BUDGET");
        assertEquals(outcome.detail(), "Stopped at the step budget.");
    }

    private static final class SilentDriver implements MobileDriverPort {
        @Override public MobileSnapshot pageSource() { return null; }
        @Override public void tap(int nodeIndex) {}
        @Override public void clear(int nodeIndex) {}
        @Override public void typeUnicode(int nodeIndex, String value) {}
        @Override public void typeAscii(int nodeIndex, String value) {}
        @Override public String readText(int nodeIndex) { return ""; }
        @Override public byte[] screenshot() { return new byte[0]; }
    }
}
