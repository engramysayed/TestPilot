package delivery.heal;

import delivery.authoring.StepIntentBinder;
import delivery.settle.SettlePolicy;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class HealPromptSafetyTest {
    @Test public void secretIntentAndPasswordNeverEchoedInBlock() {
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_PASS, "Enter password");
        assertTrue(HealPromptSafety.isSecretIntent(intent));
        String block = HealPromptSafety.safetyBlock(true, SettlePolicy.SideEffects.UNKNOWN, List.of(), false);
        assertTrue(block.contains("secret=true"));
        assertTrue(block.contains("NEVER echo"));
        assertFalse(block.toLowerCase().contains("hunter2"));
        assertTrue(block.contains("${TARGET_PASSWORD}") || block.contains("TARGET_PASSWORD"));
    }

    @Test public void mutatingSideEffectsForbidInventedRetries() {
        String block = HealPromptSafety.safetyBlock(false, SettlePolicy.SideEffects.MUTATING,
                List.of("css: #save → timeout"), false);
        assertTrue(block.contains("sideEffects=mutating"));
        assertTrue(block.contains("do NOT invent mutating retries"));
        assertTrue(block.contains("Banned locators"));
        assertTrue(block.contains("css: #save"));
        // golden snippet anchors
        assertTrue(block.startsWith("## Safety (must obey)\n"));
    }

    @Test public void crashForcesAbortInstruction() {
        assertTrue(HealPromptSafety.looksLikeCrash("Aw Snap! page crashed", "<html/>"));
        String block = HealPromptSafety.safetyBlock(false, SettlePolicy.SideEffects.NONE, List.of(), true);
        assertTrue(block.contains("CRASH/ERROR PAGE DETECTED"));
        assertTrue(block.contains("\"mode\":\"abort\"") || block.contains("mode\":\"abort\""));
    }

    @Test public void assertIntentIsNoneSideEffects() {
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE, "See Welcome");
        assertEquals(HealPromptSafety.sideEffectsOf(intent), SettlePolicy.SideEffects.NONE);
    }
}
