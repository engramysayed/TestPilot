package delivery.job;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TargetPageRecoveryTest {

    @Test
    public void reopensExcelPathWhenCurrentUrlIsADifferentPage() {
        Assert.assertTrue(TargetPageRecovery.shouldReopenExcelPath(
                "https://web.example.com/login/", "/reg/"));
        Assert.assertFalse(TargetPageRecovery.shouldReopenExcelPath(
                "https://web.example.com/reg/?x=1", "/reg/"));
        Assert.assertFalse(TargetPageRecovery.shouldReopenExcelPath(
                "https://example.com/reg", "/reg/"));
        Assert.assertFalse(TargetPageRecovery.shouldReopenExcelPath(null, "/reg/"));
        Assert.assertFalse(TargetPageRecovery.shouldReopenExcelPath("https://example.com/login/", null));
    }

    @Test
    public void signupCopyBeatsLoginCopy() {
        Assert.assertTrue(TargetPageRecovery.signupEntryScore(
                "a", "Create new account", "/r.php") > 0);
        Assert.assertTrue(TargetPageRecovery.signupEntryScore(
                "a", "Create new account", "/r.php")
                > TargetPageRecovery.signupEntryScore("a", "Log in", "/login"));
        Assert.assertEquals(TargetPageRecovery.signupEntryScore(
                "a", "Log in", "https://example.com/login/"), 0);
    }

    @Test
    public void formSubmitLeaveIsRecoverable() {
        Assert.assertTrue(delivery.authoring.StepIntentBinder.wantsFormSubmit("Click the Submit button"));
        Assert.assertTrue(TargetPageRecovery.shouldReopenExcelPath(
                "https://web.example.com/login/", "/reg/"));
        ProvenStep nav = new ProvenStep(
                "TC1", "Page", "browserAction", "navigate",
                "", "", "/reg/", "", "", true, "heal:invent:navigate");
        Assert.assertTrue(ProvePhase.onlyExcelPathNavigate(java.util.List.of(nav)));
        Assert.assertFalse(ProvePhase.onlyExcelPathNavigate(java.util.List.of(
                new ProvenStep("TC1", "Page", "elementAction", "click",
                        "id", "submit", "", "", "", true, "intent:CLICK"))));
    }

    @Test
    public void fieldAssertOnLoginPageIsRecoverable() {
        Assert.assertTrue(TargetPageRecovery.isRecoverableFieldIntent(
                new delivery.authoring.StepIntentBinder.IntentLine(
                        delivery.authoring.StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                        "Confirm the First name field is visible")));
        Assert.assertTrue(TargetPageRecovery.shouldReopenExcelPath(
                "https://web.example.com/login/", "/reg/"));
    }
}
