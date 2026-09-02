package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HtmlLocatorPresenceTest {
    @Test
    public void findsDataTestInHtml() {
        String html = "<body><a data-test=\"shopping-cart-link\" href=\"cart.html\"></a></body>";
        Assert.assertTrue(HtmlLocatorPresence.present("data-test", "shopping-cart-link", html));
        Assert.assertFalse(HtmlLocatorPresence.present("data-test", "missing", html));
    }

    @Test
    public void findsCssAttrInHtml() {
        String html = "<body><div data-test=\"inventory-container\"></div></body>";
        Assert.assertTrue(HtmlLocatorPresence.present(
                "css", "div[data-test='inventory-container']", html));
    }

    @Test
    public void listsDataTestValues() {
        String html = "<body><a data-test=\"shopping-cart-link\"></a><div data-test=\"inventory-container\"></div></body>";
        Assert.assertEquals(
                HtmlLocatorPresence.listDataTestValues(html, 10),
                java.util.List.of("shopping-cart-link", "inventory-container"));
    }
}
