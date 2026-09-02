package delivery.heal;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class HealWorkbookPatcherTest {
    private static ProvenStep clear(String locator) {
        return new ProvenStep("TC1", "Page", "elementAction", "clear", "css", locator,
                "", "", "", true, "heal:recovery");
    }

    @Test
    public void clearEmail_rewritesEnterStep_andBlanksTestData() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "Empty email", "",
                "1. Enter in the Email field\n2. Click Login",
                "Error shown", "P1", "", "",
                "user@x.com\n", "EXECUTE");
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(
                tc, List.of(clear("input[name='email']")), List.of("Keep Leave-empty on email"));
        Assert.assertTrue(r.cellsChanged());
        Assert.assertTrue(r.patchedCase().steps().toLowerCase().contains("leave"));
        Assert.assertTrue(r.patchedCase().steps().toLowerCase().contains("email"));
        String[] data = r.patchedCase().testData().split("\n", -1);
        Assert.assertTrue(data[0].isBlank());
        Assert.assertFalse(r.appliedSummaries().isEmpty());
        Assert.assertEquals(r.unmatchedNotes(), List.of("Keep Leave-empty on email"));
    }

    @Test
    public void alreadyLeaveEmpty_blanksTestDataOnly() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "",
                "1. Leave the Email field empty\n2. Click Login",
                "ok", "P1", "", "",
                "oops@x.com\n", "EXECUTE");
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(
                tc, List.of(clear("#email")), List.of());
        Assert.assertTrue(r.cellsChanged());
        Assert.assertTrue(r.patchedCase().testData().split("\n", -1)[0].isBlank());
        Assert.assertTrue(r.patchedCase().steps().toLowerCase().contains("leave the email"));
    }

    @Test
    public void clickAndNavigate_doNotRewrite() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "", "1. Enter in the Email field", "ok", "P1", "", "",
                "a@b.c", "EXECUTE");
        ProvenStep click = new ProvenStep("TC1", "Page", "elementAction", "click", "css", "#login",
                "", "", "", true, "heal:recovery");
        ProvenStep nav = new ProvenStep("TC1", "Page", "elementAction", "navigate", "url", "/login",
                "", "", "", true, "heal:recovery");
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(tc, List.of(click, nav),
                List.of("Free-form note only"));
        Assert.assertFalse(r.cellsChanged());
        Assert.assertEquals(r.patchedCase().steps(), tc.steps());
        Assert.assertEquals(r.unmatchedNotes(), List.of("Free-form note only"));
    }

    @Test
    public void doesNotAddSteps() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "", "1. Click Login", "ok", "P1", "", "", "", "EXECUTE");
        long linesBefore = tc.steps().lines().count();
        HealWorkbookPatcher.PatchResult r = HealWorkbookPatcher.patch(
                tc, List.of(clear("input[type=email]")), List.of("note"));
        Assert.assertEquals(r.patchedCase().steps().lines().count(), linesBefore);
    }
}
