package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PageAccumulatorMergeTest {
    @Test
    public void accumulatorKeepsDistinctLoginIdAndSubmitFields() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(new ProvenStep("TC", "FormAuthentication", "elementAction", "click",
                "id", "login", "", "", "", true, "click_login"));
        acc.add(new ProvenStep("TC", "FormAuthentication", "elementAction", "click",
                "cssSelector", "button[type='submit']", "", "", "", true, "click_login"));
        PageAccumulator.PageModel page = acc.pages().get("FormAuthentication");
        Assert.assertEquals(page.fields().size(), 2, String.valueOf(page.fields()));
        Assert.assertEquals(page.methods().size(), 2, String.valueOf(page.methods()));
        Assert.assertTrue(page.fields().stream().anyMatch(f -> "login".equals(f.value())), String.valueOf(page.fields()));
        Assert.assertTrue(page.fields().stream().anyMatch(f -> f.value().contains("submit")), String.valueOf(page.fields()));
    }

    @Test
    public void accumulatorDoesNotTreatBareIdAsSameControlAsSubmit() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(new ProvenStep("TC", "FormAuthentication", "elementAction", "click",
                "id", "login", "", "", "", true, ""));
        acc.add(new ProvenStep("TC", "FormAuthentication", "elementAction", "click",
                "cssSelector", "button[type='submit']", "", "", "", true, ""));
        PageAccumulator.PageModel page = acc.pages().get("FormAuthentication");
        Assert.assertEquals(page.fields().size(), 2, String.valueOf(page.fields()));
        Assert.assertTrue(page.methods().stream().anyMatch(m -> m.name().contains("Login")),
                String.valueOf(page.methods()));
    }

    @Test
    public void accumulatorDoesNotMergeUnrelatedInventoryButtons() {
        PageAccumulator acc = new PageAccumulator();
        acc.add(new ProvenStep("TC", "Inventory", "elementAction", "click",
                "id", "add-to-cart-sauce-labs-backpack", "", "", "", true, ""));
        acc.add(new ProvenStep("TC", "Inventory", "elementAction", "click",
                "id", "add-to-cart-sauce-labs-bike-light", "", "", "", true, ""));
        acc.add(new ProvenStep("TC", "Inventory", "elementAction", "click",
                "data-test", "shopping-cart-link", "", "", "", true, ""));
        PageAccumulator.PageModel page = acc.pages().get("Inventory");
        Assert.assertEquals(page.fields().size(), 3, "backpack, bike light, and cart must stay distinct");
        Assert.assertTrue(page.methods().stream().anyMatch(m -> m.name().contains("Backpack")),
                "missing backpack click method: " + page.methods());
        Assert.assertTrue(page.methods().stream().anyMatch(m ->
                        m.name().contains("Bike_Light") || m.name().contains("Bike Light")),
                "missing bike light click method: " + page.methods());
        Assert.assertTrue(page.methods().stream().anyMatch(m ->
                        m.name().contains("Shopping_Cart") || m.name().contains("Cart")),
                "missing cart click method: " + page.methods());
        Assert.assertTrue(page.fields().stream().anyMatch(f ->
                "add-to-cart-sauce-labs-backpack".equals(f.value())));
        Assert.assertTrue(page.fields().stream().anyMatch(f ->
                f.value() != null && f.value().contains("shopping-cart-link")));
    }
}
