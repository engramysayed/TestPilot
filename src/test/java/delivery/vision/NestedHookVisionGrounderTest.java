package delivery.vision;

import delivery.authoring.NestedPreferredHook;
import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class NestedHookVisionGrounderTest {
    @Test
    public void buildLocator_usesPreferredAxisHook() {
        GroundedNode node = new GroundedNode(
                "a", "", "", "", "", "link", true, true, "a",
                "Bill Payment Settlement", "data-testid",
                "data-axis-test-id", "side-nav-bill-payment-settlement-link");
        Optional<delivery.authoring.LocatorCandidate> loc = ElementGrounder.buildLocator(node);
        Assert.assertTrue(loc.isPresent());
        Assert.assertEquals(loc.get().strategy(), "css");
        Assert.assertTrue(loc.get().value().contains("data-axis-test-id"));
        Assert.assertTrue(loc.get().value().contains("side-nav-bill-payment-settlement-link"));
    }

    @Test
    public void rewriteAfterVisionXpath_usesChildHook() {
        String html = """
                <html><body><aside class="sidebar"><li role="menuitem">
                <a data-axis-test-id="side-nav-bill-payment-settlement-link">Bill Payment Settlement</a>
                </li></aside></body></html>
                """;
        ProvenStep vision = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK [catalog xpath]");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(vision, html);
            Assert.assertEquals(out.locatorStrategy(), "css");
        }
    }
}
