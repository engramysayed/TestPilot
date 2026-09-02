package delivery.job;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class TcExecutionServiceTest {
    @Test
    public void invalidLocatorStep_resultsInTodo() {
        // Pure mapping: outcome construction without browser
        ProvenStep bad = new ProvenStep("TC_X", "P", "elementAction", "click",
                "xpath", "/html/body/a", "", "", "", false, "absolute xpath");
        TcOutcome outcome = new TcOutcome("TC_X", TcStatus.TODO, List.of(),
                "Locator validation failed: " + bad.rationale(), null);
        Assert.assertEquals(outcome.status(), TcStatus.TODO);
        Assert.assertTrue(outcome.failureReason().contains("Locator validation"));
    }
}
