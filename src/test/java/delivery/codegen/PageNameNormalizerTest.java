package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PageNameNormalizerTest {
    @Test
    public void aliasesCollapseToUrlStem() {
        Assert.assertEquals(PageNameNormalizer.canonical("Login", "FormAuthentication"), "FormAuthentication");
        Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", "FormAuthentication"), "FormAuthentication");
        Assert.assertEquals(PageNameNormalizer.canonical("TargetLogin", "FormAuthentication"), "FormAuthentication");
        Assert.assertEquals(PageNameNormalizer.canonical("Page", "FormAuthentication"), "FormAuthentication");
        Assert.assertEquals(PageNameNormalizer.canonical("", "FormAuthentication"), "FormAuthentication");
        Assert.assertEquals(PageNameNormalizer.canonical("Secure", "FormAuthentication"), "Secure");
    }

    @Test
    public void blankStemFallsBackToLoginForAliases() {
        Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", "Page"), "Login");
        Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", "Home"), "Login");
        Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", ""), "Login");
    }
}
