package delivery.job;

import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AssertHealPolicyTest {

    @Test
    public void clickBindMissMayHeal() {
        StepIntentBinder.IntentLine click = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar.");
        Assert.assertTrue(ProvePhase.mayHeal(click, "No DOM candidate"));
    }

    @Test
    public void failedAssertDoesNotHeal() {
        StepIntentBinder.IntentLine assertVisible = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                "Confirm the text \"Upload Transaction List\" is visible");
        Assert.assertFalse(ProvePhase.mayHeal(assertVisible,
                "false -> text assertion timed out waiting for: Upload Transaction List"));
    }
}
