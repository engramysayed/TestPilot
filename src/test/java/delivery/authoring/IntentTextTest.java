package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IntentTextTest {

    @Test
    public void wholeWordNotSubstring() {
        Assert.assertFalse(IntentText.containsToken("Address", "add"));
        Assert.assertTrue(IntentText.containsToken("Add to cart", "add"));
        Assert.assertTrue(IntentText.containsToken("button[data-testid='add-to-cart']", "add"));
        Assert.assertTrue(IntentText.containsToken("placeOrderButton", "order"));
        Assert.assertFalse(IntentText.containsToken("Unsaved changes", "save"));
    }

    @Test
    public void normalizeCollapsesWhitespaceAndLowercases() {
        Assert.assertEquals(IntentText.normalize("  Click   The   Save  "), "click the save");
    }
}
