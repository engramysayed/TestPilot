package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.heal.FailedLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class FailedLocatorCollectionTest {

    @Test
    public void recordFailuresCapturesStrategyValueAndError() {
        ProvenStep step = new ProvenStep(
                "TC", "Page", "elementAction", "click",
                "css", "a[href='/upstream']", "", "", "", true, "intent:CLICK");
        List<FailedLocator> failed = ProvePhase.recordFailures(
                List.of(step), "element not interactable: element has zero size");
        Assert.assertEquals(failed.size(), 1);
        Assert.assertEquals(failed.get(0).strategy(), "css");
        Assert.assertEquals(failed.get(0).value(), "a[href='/upstream']");
        Assert.assertTrue(failed.get(0).errorSummary().contains("zero size"));
    }

    @Test
    public void recordFailuresDedupsSameLocator() {
        ProvenStep step = new ProvenStep(
                "TC", "Page", "elementAction", "click",
                "id", "go", "", "", "", true, "intent:CLICK");
        List<FailedLocator> failed = ProvePhase.recordFailures(
                List.of(step, step), "timeout");
        Assert.assertEquals(failed.size(), 1);
    }
}
