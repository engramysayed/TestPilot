package delivery.vision;

import delivery.codegen.ProvenStep;
import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class VisionTriggersTest {

    @Test
    public void strongBindNotWeak() {
        ProvenStep ok = new ProvenStep("T", "P", "elementAction", "click",
                "id", "loginBtn", "", "", "", true, "ok");
        Assert.assertFalse(VisionTriggers.isWeakBind(List.of(ok)));
    }

    @Test
    public void ambiguousIsWeak() {
        ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
                "", "", "", "", "", false, "AMBIGUOUS:CLICK:c1,c2");
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
    }

    @Test
    public void emptyBatchIsWeak() {
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of()));
    }

    @Test
    public void unvalidatedIsWeak() {
        ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
                "id", "x", "", "", "", false, "invalid locator");
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
    }

    @Test
    public void noDomCandidateIsWeak() {
        ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
                "", "", "", "", "", false, "No DOM candidate for intent CLICK: Click X");
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
    }

    @Test
    public void weakCandidateMatchIsWeak() {
        ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
                "", "", "", "", "", false, "Weak candidate match for intent CLICK: Click X");
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
    }

    @Test
    public void noDistinctiveTokenIsWeak() {
        ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
                "", "", "", "", "", false, "No distinctive-token match for intent CLICK: Click X");
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
    }

    @Test
    public void noActionControlMatchingIsWeak() {
        ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
                "", "", "", "", "", false,
                "No action control matching distinctive tokens for intent CLICK: Click X");
        Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
    }

    @Test
    public void eligibleKinds() {
        for (StepIntentBinder.IntentKind kind : List.of(
                StepIntentBinder.IntentKind.CLICK,
                StepIntentBinder.IntentKind.CLICK_LOGIN,
                StepIntentBinder.IntentKind.TYPE_FIELD,
                StepIntentBinder.IntentKind.TYPE_USER,
                StepIntentBinder.IntentKind.TYPE_PASS,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE)) {
            Assert.assertTrue(VisionTriggers.isEligible(
                    new StepIntentBinder.IntentLine(kind, "step")));
        }
    }
}
