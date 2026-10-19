package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class NestedPreferredHookBindTest {
    /** Forces named-action multi-match: both carry Add verb + quoted label; hook vs li are not locator twins. */
    private static List<DomCandidate> hookAndLiNamedActionCandidates() {
        return List.of(
                new DomCandidate("c2", "xpath",
                        "//li[@data-action='add'][normalize-space(.)='Bill Payment Settlement']",
                        "li", "Bill Payment Settlement"),
                new DomCandidate("c1", "css",
                        "a[data-axis-test-id='add-side-nav-bill-payment-settlement-link']",
                        "a", "Bill Payment Settlement"));
    }

    private static StepIntentBinder.IntentLine addBillPaymentSettlementIntent() {
        return new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Add the 'Bill Payment Settlement' item in the Support sub-menu.");
    }

    private static final String HTML = """
            <html><body><aside class="sidebar">
            <li role="menuitem">
              <a data-axis-test-id="side-nav-bill-payment-settlement-link"
                 href="/bill-payment-settlement">Bill Payment Settlement</a>
            </li>
            </aside></body></html>
            """;

    @Test
    public void bindSingle_namedActionHookAndLi_pickNamedClearsAmbiguous() {
        var intent = addBillPaymentSettlementIntent();
        List<DomCandidate> cs = hookAndLiNamedActionCandidates();
        Assert.assertTrue(StepIntentBinder.intentRequiresNamedActionControl(intent.text()));
        List<DomCandidate> namedMatches = cs.stream()
                .filter(c -> StepIntentBinder.candidateMatchesNamedAction(intent.text(), c, cs))
                .toList();
        Assert.assertEquals(namedMatches.size(), 2, "fixture must hit multiple named controls");
        Assert.assertTrue(NestedPreferredHook.pickNamed(namedMatches).isPresent());

        StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(intent, "TC_3", cs, List.of());
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertFalse(bound.rejectReason().contains("AMBIGUOUS"));
        Assert.assertTrue(bound.steps().get(0).locatorValue().contains("data-axis-test-id"));
    }

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
    public void authorIntent_boundLiXpath_rewritesToInnerHookCss() throws Exception {
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click the 'Bill Payment Settlement' in the Support sub-menu.");
        List<DomCandidate> liOnlyCatalog = List.of(
                new DomCandidate("li1", "xpath",
                        "//li[contains(normalize-space(.),'Bill Payment Settlement')]",
                        "li", "Bill Payment Settlement"));
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(
                    intent, "TC_3", liOnlyCatalog, List.of());
            Assert.assertTrue(bound.ok(), bound.rejectReason());
            Assert.assertEquals(bound.steps().get(0).locatorStrategy(), "xpath",
                    "bind must keep li xpath before authorIntent rewrite");

            List<ProvenStep> steps = authoring.authorIntent(
                    "TC_3", intent, HTML, null, false, List.of(), List.of(), liOnlyCatalog);
            Assert.assertFalse(steps.isEmpty());
            Assert.assertTrue(steps.get(0).validated());
            Assert.assertEquals(steps.get(0).locatorStrategy(), "css");
            Assert.assertTrue(steps.get(0).locatorValue().contains("data-axis-test-id"));
            Assert.assertTrue(steps.get(0).locatorValue().contains("side-nav-bill-payment-settlement-link"));
        }
    }
}
