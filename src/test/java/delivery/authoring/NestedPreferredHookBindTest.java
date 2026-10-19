package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class NestedPreferredHookBindTest {
    private static final String HTML = """
            <html><body><aside class="sidebar">
            <li role="menuitem">
              <a data-axis-test-id="side-nav-bill-payment-settlement-link"
                 href="/bill-payment-settlement">Bill Payment Settlement</a>
            </li>
            </aside></body></html>
            """;

    @Test
    public void bindSingle_sameNameHookAndLi_picksHook() {
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "css",
                        "a[data-axis-test-id='side-nav-bill-payment-settlement-link']",
                        "a", "Bill Payment Settlement"),
                new DomCandidate("c2", "xpath",
                        "//li[normalize-space(.)='Bill Payment Settlement']",
                        "li", "Bill Payment Settlement"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click the 'Bill Payment Settlement' in the Support sub-menu.");
        StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(intent, "TC_3", cs, List.of());
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(bound.steps().get(0).locatorValue().contains("data-axis-test-id"));
    }

    @Test
    public void authorIntent_xpathOnly_rewritesInnerHook() throws Exception {
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click the 'Bill Payment Settlement' in the Support sub-menu.");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            List<ProvenStep> steps = authoring.authorIntent(
                    "TC_3", intent, HTML, null, false, List.of(), List.of(), null);
            Assert.assertFalse(steps.isEmpty());
            Assert.assertTrue(steps.get(0).validated());
            Assert.assertEquals(steps.get(0).locatorStrategy(), "css");
            Assert.assertTrue(steps.get(0).locatorValue().contains("side-nav-bill-payment-settlement-link"));
        }
    }
}
