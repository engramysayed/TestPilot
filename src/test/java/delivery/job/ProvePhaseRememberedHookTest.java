package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ProvePhaseRememberedHookTest {
    private static final String HTML = """
            <html><body><aside class="sidebar"><li role="menuitem">
            <a data-axis-test-id="side-nav-bill-payment-settlement-link">Bill Payment Settlement</a>
            </li></aside></body></html>
            """;

    @Test
    public void rememberedWrapperXpath_rewritesToNestedHookBeforeExecute() {
        ProvenStep remembered = new ProvenStep(
                "TC_3", "Page", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true,
                "memory:CLICK|click the 'bill payment settlement' in the support sub-menu.");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = ProvePhase.rewriteRememberedLocator(remembered, HTML);
            Assert.assertEquals(out.locatorStrategy(), "css");
            Assert.assertTrue(out.locatorValue().contains("data-axis-test-id"), out.locatorValue());
            Assert.assertTrue(out.locatorValue().contains("side-nav-bill-payment-settlement-link"),
                    out.locatorValue());
            Assert.assertTrue(out.rationale().startsWith("memory:"), out.rationale());
        }
    }

    @Test
    public void rememberedWrapperXpath_duplicateOutside_rewritesToScopedHook() {
        String html = HTML.replace("</aside>",
                "<a data-axis-test-id=\"side-nav-bill-payment-settlement-link\">copy</a></aside>");
        ProvenStep remembered = new ProvenStep(
                "TC_3", "Page", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true,
                "memory:CLICK|click the 'bill payment settlement' in the support sub-menu.");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = ProvePhase.rewriteRememberedLocator(remembered, html);
            Assert.assertEquals(out.locatorStrategy(), "xpath");
            Assert.assertEquals(out.locatorValue(),
                    "//li[normalize-space(.)='Bill Payment Settlement']"
                            + "//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']");
            Assert.assertTrue(out.rationale().startsWith("memory:"), out.rationale());
        }
    }
}
