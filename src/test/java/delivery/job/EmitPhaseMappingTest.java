package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class EmitPhaseMappingTest {
    @Test
    public void partialDraftMapsToPartialOutcome() {
        ProvenStep step = new ProvenStep(
                "TC1", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        TcDraft draft = new TcDraft(
                "TC1", "t", "steps", "exp", TcDraftStatus.PARTIAL,
                List.of(step), List.of(), false, 3, "Click Finish",
                "No DOM candidate", "", 2, "https://x/checkout");
        TcOutcome outcome = EmitPhase.toOutcome(draft);
        Assert.assertEquals(outcome.status(), TcStatus.PARTIAL);
        Assert.assertTrue(outcome.failureReason().contains("Blocked at step 3"));
        Assert.assertTrue(outcome.failureReason().contains("Click Finish"));
        Assert.assertEquals(outcome.provenSteps().size(), 1);
    }

    @Test
    public void reusedMapsToPassed() {
        TcDraft draft = new TcDraft(
                "TC2", "t", "steps", "exp", TcDraftStatus.REUSED,
                List.of(), List.of(), false, -1, "", "reused", "", 0, "");
        Assert.assertEquals(EmitPhase.toOutcome(draft).status(), TcStatus.PASSED);
    }
}
