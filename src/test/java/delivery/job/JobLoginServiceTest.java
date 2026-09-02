package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JobLoginServiceTest {

    @Test
    public void loginClickCssIncludesSubmitWithoutTypeAttr() {
        String css = JobLoginService.loginClickCss().toString();
        Assert.assertTrue(css.contains("#submit"), css);
        Assert.assertTrue(css.contains("button#submit") || css.contains("#submit"), css);
        Assert.assertTrue(css.contains("button[type='submit']") || css.contains("type='submit'"), css);
    }

    @Test
    public void loginClickXpathMatchesSubmitOrLoginText() {
        String xp = JobLoginService.loginClickXpath().toString().toLowerCase();
        Assert.assertTrue(xp.contains("submit"), xp);
        Assert.assertTrue(xp.contains("login") || xp.contains("log in"), xp);
    }
}
