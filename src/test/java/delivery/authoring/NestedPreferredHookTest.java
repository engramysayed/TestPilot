package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class NestedPreferredHookTest {
    private static final String ROW = """
            <html><body><aside class="sidebar">
            <li role="menuitem">
              <a data-axis-test-id="side-nav-bill-payment-settlement-link"
                 href="/bill-payment-settlement">Bill Payment Settlement</a>
            </li>
            </aside></body></html>
            """;

    @Test
    public void rewrite_innerUniqueHook_becomesCss() {
        ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(xpath, ROW);
            Assert.assertEquals(out.locatorStrategy(), "css");
            Assert.assertTrue(out.locatorValue().contains("data-axis-test-id"));
            Assert.assertTrue(out.locatorValue().contains("side-nav-bill-payment-settlement-link"));
        }
    }

    @Test
    public void rewrite_duplicateHookOutsideRow_becomesScopedXpath() {
        String html = ROW.replace("</aside>",
                "<a data-axis-test-id=\"side-nav-bill-payment-settlement-link\">copy</a></aside>");
        ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(xpath, html);
            Assert.assertEquals(out.locatorStrategy(), "xpath");
            Assert.assertEquals(out.locatorValue(),
                    "//li[normalize-space(.)='Bill Payment Settlement']"
                            + "//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']");
        }
    }

    @Test
    public void rewrite_twoHooksInsideRow_keepsXpath() {
        String html = """
                <html><body><aside class="sidebar">
                <li role="menuitem">
                  <a data-axis-test-id="side-nav-bill-payment-settlement-link">Bill Payment Settlement</a>
                  <button data-axis-test-id="other-hook">x</button>
                </li>
                </aside></body></html>
                """;
        ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(xpath, html);
            Assert.assertEquals(out.locatorStrategy(), "xpath");
            Assert.assertEquals(out.locatorValue(), xpath.locatorValue());
        }
    }

    @Test
    public void pickNamed_oneHookAmongSameLabel_returnsHook() {
        DomCandidate hook = new DomCandidate("c1", "css",
                "a[data-axis-test-id='side-nav-bill-payment-settlement-link']",
                "a", "Bill Payment Settlement");
        DomCandidate li = new DomCandidate("c2", "xpath",
                "//li[normalize-space(.)='Bill Payment Settlement']",
                "li", "Bill Payment Settlement");
        Optional<DomCandidate> pick = NestedPreferredHook.pickNamed(List.of(hook, li));
        Assert.assertTrue(pick.isPresent());
        Assert.assertEquals(pick.get().id(), "c1");
    }

    @Test
    public void pickNamed_twoHooks_empty() {
        DomCandidate a = new DomCandidate("c1", "css", "button[data-axis-test-id='settings-1']",
                "button", "Settings");
        DomCandidate b = new DomCandidate("c2", "css", "button[data-axis-test-id='settings-2']",
                "button", "Settings");
        Assert.assertTrue(NestedPreferredHook.pickNamed(List.of(a, b)).isEmpty());
    }

    @Test
    public void rewrite_skipsAssert() {
        ProvenStep as = new ProvenStep("TC_3", "P", "elementAction", "assert",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "visible", "", true, "intent:ASSERT_VISIBLE");
        ProvenStep out = NestedPreferredHook.rewrite(as, ROW);
        Assert.assertEquals(out.locatorValue(), as.locatorValue());
    }
}
