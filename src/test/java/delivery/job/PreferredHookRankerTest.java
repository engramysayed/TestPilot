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
            Assert.assertEquals(ProveWiring.currentScore(s), LocatorRanker.TEST_ID, 0.001);
        }
    }
}
