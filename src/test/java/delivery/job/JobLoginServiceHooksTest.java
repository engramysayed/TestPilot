package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class JobLoginServiceHooksTest {

    @Test
    public void usernameCssUsesConfiguredHookThenLegacy() {
        String css = JobLoginService.usernameCss(List.of("data-qa"));
        Assert.assertTrue(css.contains("data-qa"), css);
        Assert.assertTrue(css.contains("*='user'") || css.contains("*='User'") || css.contains("*='username'"), css);
        Assert.assertTrue(css.contains("input[name='username']"), css);
        Assert.assertTrue(css.contains("input[type='email']"), css);
    }

    @Test
    public void passwordCssIncludesHookPasswordPatterns() {
        String css = JobLoginService.passwordCss(List.of("data-qa"));
        Assert.assertTrue(css.contains("data-qa"), css);
        Assert.assertTrue(css.toLowerCase().contains("password"), css);
        Assert.assertTrue(css.contains("input[type='password']"), css);
    }

    @Test
    public void submitCssIncludesHookSignInPatterns() {
        String css = JobLoginService.submitCss(List.of("data-test"));
        Assert.assertTrue(css.contains("data-test"), css);
        Assert.assertTrue(css.toLowerCase().contains("sign") || css.toLowerCase().contains("login"), css);
        Assert.assertTrue(css.contains("button[type='submit']"), css);
    }

    @Test
    public void emptyHooksUsesLegacyOnlyNoHardcodedVendorAttr() {
        String css = JobLoginService.usernameCss(List.of());
        Assert.assertTrue(css.contains("input[name='username']"), css);
        Assert.assertFalse(css.contains("data-axis-test-id"), css);
        Assert.assertFalse(css.contains("input[data-testid*="), css);
    }
}
