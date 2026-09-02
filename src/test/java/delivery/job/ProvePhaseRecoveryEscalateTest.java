package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.heal.HealResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Cursor escalate after Ollama must route recovery-tier heals through recovery → retry intent,
 * never treat recovery steps as intent success.
 */
public class ProvePhaseRecoveryEscalateTest {

    private static ProvenStep clearEmail() {
        return new ProvenStep(
                "TC1", "Page", "elementAction", "clear", "css", "input[name='email']",
                "", "", "", true, "heal:recovery");
    }

    @Test
    public void recoveryTier_isDetected() {
        HealResult recovery = HealResult.recovery(List.of(clearEmail()), List.of("note"), "filled");
        Assert.assertTrue(ProvePhase.isRecoveryTier(recovery));
        Assert.assertFalse(ProvePhase.isRecoveryTier(HealResult.success(List.of(clearEmail()), "cursor")));
        Assert.assertFalse(ProvePhase.isRecoveryTier(HealResult.fail("nope")));
        Assert.assertFalse(ProvePhase.isRecoveryTier(null));
    }

    @Test
    public void escalate_recovery_routesToRecoveryRetryNotClassicExecute() {
        HealResult recovery = HealResult.recovery(List.of(clearEmail()), List.of(), "email filled");
        Assert.assertEquals(
                ProvePhase.decideEscalateHeal(recovery, 0),
                ProvePhase.EscalateHealDecision.RUN_RECOVERY_AND_RETRY_INTENT);
    }

    @Test
    public void escalate_recoveryAlreadyAttempted_failsClosed() {
        HealResult recovery = HealResult.recovery(List.of(clearEmail()), List.of(), "again");
        Assert.assertEquals(
                ProvePhase.decideEscalateHeal(recovery, ProvePhase.MAX_RECOVERY_ATTEMPTS),
                ProvePhase.EscalateHealDecision.RECOVERY_ALREADY_ATTEMPTED_FAIL);
    }

    @Test
    public void escalate_secondRecoveryStillAllowedOnce() {
        HealResult recovery = HealResult.recovery(List.of(clearEmail()), List.of(), "retry");
        Assert.assertEquals(
                ProvePhase.decideEscalateHeal(recovery, 1),
                ProvePhase.EscalateHealDecision.RUN_RECOVERY_AND_RETRY_INTENT);
    }

    @Test
    public void escalate_classicCursor_executesAsIntentFix() {
        HealResult classic = HealResult.success(List.of(clearEmail()), "cursor");
        Assert.assertEquals(
                ProvePhase.decideEscalateHeal(classic, 0),
                ProvePhase.EscalateHealDecision.EXECUTE_AS_INTENT_FIX);
    }

    @Test
    public void escalate_failedHeal_skips() {
        Assert.assertEquals(
                ProvePhase.decideEscalateHeal(HealResult.fail("exhausted"), 0),
                ProvePhase.EscalateHealDecision.HEAL_FAILED);
    }
}
