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
}
