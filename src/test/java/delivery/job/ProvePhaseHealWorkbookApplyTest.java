package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.heal.HealResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProvePhaseHealWorkbookApplyTest {

    private static ProvenStep clearEmail() {
        return new ProvenStep(
                "TC_01", "Page", "elementAction", "clear", "css", "input[name='email']",
                "", "", "", true, "heal:recovery");
    }

    private static ConversionJobRequest sampleRequest() {
        return new ConversionJobRequest(
                "p1",
                Path.of("x.xlsx"),
                "https://ex.test",
                "",
                "",
                Path.of("."),
                Path.of("."),
                Path.of("."),
                "EXECUTE",
                "",
                "");
    }

    @Test
    public void applyBestEffort_invokesApplier_forRecovery() {
        AtomicBoolean called = new AtomicBoolean();
        ProvePhase.HealWorkbookApplier applier = (pid, tc, steps, notes, url) -> called.set(true);
        HealResult healed = HealResult.recovery(List.of(clearEmail()), List.of("n"), "t");
        ProvePhase.applyHealWorkbookPatchBestEffort(applier, sampleRequest(), "TC_01", healed);
        Assert.assertTrue(called.get());
    }

    @Test
    public void applyBestEffort_swallowsApplierFailure() {
        ProvePhase.HealWorkbookApplier applier = (a, b, c, d, e) -> {
            throw new RuntimeException("boom");
        };
        HealResult healed = HealResult.recovery(List.of(clearEmail()), List.of("n"), "t");
        ProvePhase.applyHealWorkbookPatchBestEffort(applier, sampleRequest(), "TC_01", healed);
    }

    @Test
    public void applyBestEffort_skipsNonRecovery() {
        AtomicInteger calls = new AtomicInteger();
        ProvePhase.HealWorkbookApplier applier = (a, b, c, d, e) -> calls.incrementAndGet();
        HealResult classic = HealResult.success(List.of(clearEmail()), "cursor");
        ProvePhase.applyHealWorkbookPatchBestEffort(applier, sampleRequest(), "TC_01", classic);
        Assert.assertEquals(calls.get(), 0);
    }

    @Test
    public void applyBestEffort_nullApplier_noop() {
        HealResult healed = HealResult.recovery(List.of(clearEmail()), List.of("n"), "t");
        ProvePhase.applyHealWorkbookPatchBestEffort(null, sampleRequest(), "TC_01", healed);
    }
}
