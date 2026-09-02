package delivery.codegen;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PageClustererTest {
    @Test
    public void pageNameFromCheckoutPath() {
        Assert.assertEquals(
                PageClusterer.pageNameFromUrl("https://shop.example.com/checkout-step-one.html"),
                "CheckoutStepOne");
    }

    @Test
    public void pageNameFromCart() {
        Assert.assertEquals(
                PageClusterer.pageNameFromUrl("https://shop.example/cart.html"),
                "Cart");
    }

    @Test
    public void rootWwwHostUsesBrandNotWww() {
        Assert.assertEquals(
                PageClusterer.pageNameFromUrl("https://www.saucedemo.com/"),
                "Saucedemo");
        Assert.assertEquals(
                PageClusterer.pageNameFromUrl("https://www.saucedemo.com"),
                "Saucedemo");
        Assert.assertNotEquals(
                PageClusterer.pageNameFromUrl("https://www.saucedemo.com/"),
                "Www");
        Assert.assertEquals(
                PageClusterer.pageNameFromHost("www.practicetestautomation.com"),
                "Practicetestautomation");
    }

    @Test
    public void reclusterLoginUsesLoginFormUrlStemNotFinalInventory() {
        ProvenStep loginType = new ProvenStep("TC1", "Page", "elementAction", "type",
                "id", "user-name", "${TARGET_USERNAME}", "", "", true, "heuristic");
        ProvenStep body = new ProvenStep("TC1", "Inventory", "elementAction", "assert",
                "data-test", "title", "", "visible", "", true, "intent:ASSERT_VISIBLE");
        TcDraft draft = new TcDraft(
                "TC1", "t", "steps", "exp", TcDraftStatus.PASSED,
                List.of(body), List.of(loginType), true,
                -1, "", "", "", 0,
                "https://www.saucedemo.com/inventory.html",
                "none", "",
                "https://www.saucedemo.com/");
        TcDraft out = PageClusterer.reclusterDraft(draft);
        Assert.assertEquals(out.loginSteps().get(0).pageName(), "Saucedemo");
        Assert.assertEquals(out.provenSteps().get(0).pageName(), "Inventory");
    }

    @Test
    public void reclusterMapsFrozenLoginToUrlStem() {
        ProvenStep loginType = new ProvenStep("TC1", "Login", "elementAction", "type",
                "id", "username", "u", "", "", true, "heuristic");
        ProvenStep body = new ProvenStep("TC1", "Page", "elementAction", "assert",
                "id", "error", "", "textContains", "invalid", true, "intent");
        TcDraft draft = new TcDraft(
                "TC1", "t", "steps", "exp", TcDraftStatus.PASSED,
                List.of(body), List.of(loginType), true,
                -1, "", "", "", 0,
                "https://practicetestautomation.com/practice-test-login/");
        TcDraft out = PageClusterer.reclusterDraft(draft);
        Assert.assertEquals(out.loginSteps().get(0).pageName(), "PracticeTestLogin");
        Assert.assertEquals(out.provenSteps().get(0).pageName(), "PracticeTestLogin");
    }

    @Test
    public void reclusterMapsFrozenLoginFormToUrlStem() {
        ProvenStep loginType = new ProvenStep("TC1", "LoginForm", "elementAction", "type",
                "id", "username", "u", "", "", true, "heuristic");
        ProvenStep body = new ProvenStep("TC1", "Page", "elementAction", "assert",
                "id", "error", "", "textContains", "invalid", true, "intent");
        TcDraft draft = new TcDraft(
                "TC1", "t", "steps", "exp", TcDraftStatus.PASSED,
                List.of(body), List.of(loginType), true,
                -1, "", "", "", 0,
                "https://practicetestautomation.com/practice-test-login/");
        TcDraft out = PageClusterer.reclusterDraft(draft);
        Assert.assertEquals(out.loginSteps().get(0).pageName(), "PracticeTestLogin");
        Assert.assertEquals(out.provenSteps().get(0).pageName(), "PracticeTestLogin");
    }

    @Test
    public void codegenNamingAvoidsXpathInMethod() {
        ProvenStep step = new ProvenStep("TC1", "LoggedInSuccessfully", "elementAction", "assert",
                "xpath",
                "//body//*[not(self::script)][contains(normalize-space(.),'Logged In Successfully')]",
                "", "textContains", "Logged In Successfully", true, "intent");
        String method = CodegenNaming.assertMethodName(step);
        Assert.assertEquals(method, "assert_Logged_In_Successfully_Is_Visible");
        Assert.assertFalse(method.contains("body"));
        Assert.assertFalse(method.contains("script"));
        String field = CodegenNaming.locatorFieldName(step);
        Assert.assertTrue(field.endsWith("_Locator"), field);
        Assert.assertFalse(field.contains("normalize"), field);
    }
}
