package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ConversionProgressReserveTest {

    @Test
    public void phase2StartingKeepsProvePlusEmitTotal() {
        int proveUnits = 5;
        int jobTotal = proveUnits + EmitPhase.PROGRESS_UNITS;

        JobProgressTracker progress = new JobProgressTracker();
        progress.update(0, jobTotal, "Phase1 starting");
        progress.update(proveUnits, jobTotal, "Phase2 starting");

        Assert.assertEquals(progress.effectiveTotal(proveUnits), jobTotal);
        Assert.assertEquals(progress.current(), proveUnits);
        Assert.assertEquals(progress.total(), jobTotal);
        Assert.assertNotEquals(progress.current(), progress.total());
        Assert.assertTrue(progress.message().contains("Phase2"));
    }

    @Test
    public void emitBumpAdvancesCurrentWithoutResettingTotal() {
        int proveUnits = 5;
        int jobTotal = proveUnits + EmitPhase.PROGRESS_UNITS;

        JobProgressTracker progress = new JobProgressTracker();
        progress.update(proveUnits, jobTotal, "Phase2 starting");

        EmitPhase emit = new EmitPhase(progress);
        emit.bumpProgress("Phase2 emit: loading IR drafts");

        Assert.assertEquals(progress.total(), jobTotal);
        Assert.assertEquals(progress.current(), proveUnits + 1);
        Assert.assertNotEquals(progress.current(), progress.total());
    }
}
