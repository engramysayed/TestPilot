package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.job.ProvePhase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * ENABLED/DISABLED must not false-pass as mere visible.
 * Real execute types {@code enabled}/{@code disabled}; asserts never heal.
 */
public class EnabledDisabledAssertBindTest {

    private static final List<DomCandidate> SUBMIT_AND_CANCEL = List.of(
            new DomCandidate("c1", "id", "submit", "button", "Submit"),
            new DomCandidate("c2", "id", "cancel", "button", "Cancel")
    );

    private static final List<DomCandidate> TWIN_SUBMIT = List.of(
            new DomCandidate("c1", "id", "submit-primary", "button", "Submit"),
            new DomCandidate("c2", "id", "submit-secondary", "button", "Submit")
    );

    @Test
    public void classify_enabled_usesEnabledAssertType() {
        OutcomeKind.Outcome o = OutcomeKind.classify("Confirm Submit is enabled");
        Assert.assertEquals(o.kind(), OutcomeKind.Kind.ENABLED);
        Assert.assertEquals(o.assertionType(), "enabled");
        Assert.assertNotEquals(o.assertionType(), "visible");
    }

    @Test
    public void classify_disabled_usesDisabledAssertType() {
        OutcomeKind.Outcome o = OutcomeKind.classify("Confirm Submit is disabled");
        Assert.assertEquals(o.kind(), OutcomeKind.Kind.DISABLED);
        Assert.assertEquals(o.assertionType(), "disabled");
        Assert.assertNotEquals(o.assertionType(), "visible");
    }

    @Test
    public void enabledAssert_bindsNamedControl_withEnabledType() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Submit is enabled");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC", SUBMIT_AND_CANCEL, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        ProvenStep step = r.steps().get(0);
        Assert.assertEquals(step.assertionType(), "enabled");
        Assert.assertNotEquals(step.assertionType(), "visible");
        Assert.assertTrue(step.locatorValue().toLowerCase().contains("submit"), step.locatorValue());
        Assert.assertTrue(step.validated());
        Assert.assertTrue(step.rationale().toLowerCase().contains("enabled"), step.rationale());
    }

    @Test
    public void disabledAssert_bindsNamedControl_withDisabledType() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Submit is disabled");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC", SUBMIT_AND_CANCEL, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "disabled");
        Assert.assertNotEquals(r.steps().get(0).assertionType(), "visible");
    }

    @Test
    public void twinSubmit_enabled_failClosed_notVisibleTie() {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Submit is enabled");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                intent, "TC", TWIN_SUBMIT, List.of());
        Assert.assertFalse(r.ok(), r.rejectReason());
        Assert.assertFalse(r.rejectReason().startsWith("AMBIGUOUS:"),
                "must not CandidateScorer TIE: " + r.rejectReason());
        Assert.assertTrue(r.steps().isEmpty());
    }

    @Test
    public void enabledAssert_mayHealFalse() {
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm Submit is enabled");
        Assert.assertFalse(ProvePhase.mayHeal(intent, "false -> expected enabled but was disabled"));
    }
}
