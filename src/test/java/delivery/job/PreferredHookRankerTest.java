package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.snapshot.LocatorRanker;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PreferredHookRankerTest {
    @Test
    public void currentScore_axisHook_isTestId() {
        ProvenStep s = new ProvenStep("T", "P", "elementAction", "click",
                "css", "a[data-axis-test-id='side-nav-bill-payment-settlement-link']",
                "", "", "", true, "x");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            // Preferred hook outranks every other tier (shared LocatorStabilityPolicy).
            Assert.assertEquals(ProveWiring.currentScore(s), 1.0, 0.001);
        }
    }

    @Test
    public void currentScore_testIdBeatsAriaLabel_stableFirst() {
        ProvenStep tid = new ProvenStep("T", "P", "elementAction", "click",
                "css", "button[data-testid='save']", "", "", "", true, "x");
        ProvenStep aria = new ProvenStep("T", "P", "elementAction", "click",
                "css", "button[aria-label='Save']", "", "", "", true, "x");
        Assert.assertTrue(ProveWiring.currentScore(tid) > ProveWiring.currentScore(aria));
    }
}
