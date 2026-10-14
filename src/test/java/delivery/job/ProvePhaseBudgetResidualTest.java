package delivery.job;

import delivery.routing.ObservationBudget;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** AE-L4: author login/batch HTML slims must use ObservationBudget.forProve(), not hard-coded 50k. */
public class ProvePhaseBudgetResidualTest {
    @Test
    public void proveHtmlBudgetMatchesObservationBudget() {
        Assert.assertEquals(ProvePhase.proveHtmlBudget(), ObservationBudget.forProve());
    }

    @Test
    public void provePhaseSourceHasNoHardCodedFiftyThousandSlim() throws Exception {
        Path src = Path.of("src/main/java/delivery/job/ProvePhase.java");
        if (!Files.isRegularFile(src)) {
            src = Path.of("../src/main/java/delivery/job/ProvePhase.java");
        }
        Assert.assertTrue(Files.isRegularFile(src), "ProvePhase.java not found for source check");
        String body = Files.readString(src);
        Assert.assertFalse(body.contains("HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 50000)"),
                "residual hard-coded 50000 slim still present");
        Assert.assertTrue(body.contains("proveHtmlBudget()"));
    }
}
