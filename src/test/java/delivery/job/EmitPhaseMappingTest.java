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
    public void reusedWithoutProofDoesNotCountAsPassed() {
        TcDraft draft = new TcDraft(
                "TC2", "t", "steps", "exp", TcDraftStatus.REUSED,
                List.of(), List.of(), false, -1, "", "reused", "", 0, "");
        Assert.assertEquals(EmitPhase.toOutcome(draft).status(), TcStatus.TODO,
                "empty REUSED IR must not silently pass");
    }

    @Test
    public void reusedWithProofCountsAsPassedAndKeepsProvenance() {
        ProvenStep step = new ProvenStep(
                "TC2", "Home", "elementAction", "click",
                "id", "go", "", "", "", true, "proven");
        TcDraft draft = new TcDraft(
                "TC2", "t", "steps", "exp", TcDraftStatus.REUSED,
                List.of(step), List.of(), false, -1, "",
                "reused prior PASSED proof; not a fresh browser run",
                "evidence/TC2", 0, "https://example.com/home");
        TcOutcome outcome = EmitPhase.toOutcome(draft);
        Assert.assertEquals(outcome.status(), TcStatus.PASSED);
        Assert.assertEquals(outcome.provenSteps().size(), 1);
        Assert.assertTrue(outcome.failureReason().toLowerCase().contains("reused"));
        Assert.assertFalse(outcome.failureReason().isBlank());
    }
}
