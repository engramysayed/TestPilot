package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class AuthoringVerbAliasesTest {

    @Test
    public void observeWithoutAlias_isSkipped() {
        try (var ignored = AuthoringVerbAliases.activate(List.of())) {
            Assert.assertNull(StepIntentBinder.classify("observe the rates module is displayed"));
        }
    }

    @Test
    public void observeAliasAssert_isAssertVisible() {
        try (var ignored = AuthoringVerbAliases.activate(List.of(
                new AuthoringVerbAliases.Alias("observe", "assert")))) {
            Assert.assertEquals(
                    StepIntentBinder.classify("observe the rates module is displayed"),
                    StepIntentBinder.IntentKind.ASSERT_VISIBLE);
            Assert.assertTrue(StepIntentBinder.looksLikeAssert("observe the rates module is displayed"));
        }
    }

    @Test
    public void ensureIgnore_isSkipped() {
        try (var ignored = AuthoringVerbAliases.activate(List.of(
                new AuthoringVerbAliases.Alias("ensure", "ignore")))) {
            Assert.assertNull(StepIntentBinder.classify("ensure the page loaded"));
        }
    }

    @Test
    public void builtinClick_winsOverAlias() {
        try (var ignored = AuthoringVerbAliases.activate(List.of(
                new AuthoringVerbAliases.Alias("observe", "assert")))) {
            Assert.assertEquals(
                    StepIntentBinder.classify("click the rates module"),
                    StepIntentBinder.IntentKind.CLICK);
        }
    }

    @Test
    public void normalize_rejectsBuiltinTap() {
        try {
            AuthoringVerbAliases.normalize(List.of(
                    new AuthoringVerbAliases.Alias("tap", "click")));
            Assert.fail("expected collision");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().toLowerCase().contains("tap"), e.getMessage());
        }
    }

    @Test
    public void observeSubstring_isNotWholeWord() {
        try (var ignored = AuthoringVerbAliases.activate(List.of(
                new AuthoringVerbAliases.Alias("observe", "assert")))) {
            Assert.assertNull(StepIntentBinder.classify("the rates were observed on the page"));
        }
    }

    @Test
    public void seed_observeAssert() {
        List<AuthoringVerbAliases.Alias> seed = AuthoringVerbAliases.seed();
        Assert.assertEquals(seed.size(), 1);
        Assert.assertEquals(seed.get(0).word(), "observe");
        Assert.assertEquals(seed.get(0).treatAs(), "assert");
    }

    @Test
    public void wipeAliasClear_isTypeFieldAndClears() {
        try (var ignored = AuthoringVerbAliases.activate(List.of(
                new AuthoringVerbAliases.Alias("wipe", "clear")))) {
            Assert.assertEquals(
                    StepIntentBinder.classify("wipe the email field"),
                    StepIntentBinder.IntentKind.TYPE_FIELD);
            Assert.assertTrue(StepIntentBinder.isLeaveOrKeepEmptyStep("wipe the email field"));
        }
    }

    @Test
    public void dragAndHoverClassify_fromBuiltinWords() {
        Assert.assertEquals(StepIntentBinder.classify("drag the ticket card onto the done column"),
                StepIntentBinder.IntentKind.DRAG);
        Assert.assertEquals(StepIntentBinder.classify("hover over the profile menu"),
                StepIntentBinder.IntentKind.HOVER);
        Assert.assertEquals(StepIntentBinder.classify("double click the folder"),
                StepIntentBinder.IntentKind.DOUBLE_CLICK);
        Assert.assertEquals(StepIntentBinder.classify("right click the file"),
                StepIntentBinder.IntentKind.RIGHT_CLICK);
    }

    @Test
    public void normalize_acceptsClearAndDrag() {
        List<AuthoringVerbAliases.Alias> rows = AuthoringVerbAliases.normalize(List.of(
                new AuthoringVerbAliases.Alias("wipe", "clear"),
                new AuthoringVerbAliases.Alias("slide", "drag")));
        Assert.assertEquals(rows.get(0).treatAs(), "clear");
        Assert.assertEquals(rows.get(1).treatAs(), "drag");
    }
}
