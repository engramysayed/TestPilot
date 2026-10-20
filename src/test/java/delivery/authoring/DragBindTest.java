package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class DragBindTest {

    private static final String BOARD = """
            <html><body>
              <button id="ticket">Ticket card</button>
              <div id="done" role="button">Done</div>
            </body></html>
            """;

    @Test
    public void dragOnto_bindsSourceAndDropTarget() throws Exception {
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.DRAG,
                "Drag the Ticket card onto the Done column");
        AuthoringService authoring = new AuthoringService(null, new LocatorValidator());
        List<ProvenStep> steps = authoring.authorIntent("TC_D", intent, BOARD, null, false);
        Assert.assertTrue(steps.get(0).validated(), steps.get(0).rationale());
        Assert.assertEquals(steps.get(0).action(), "drag");
        Assert.assertTrue(steps.get(0).locatorValue().contains("ticket")
                        || steps.get(0).locatorValue().toLowerCase().contains("ticket"),
                steps.get(0).locatorValue());
        Assert.assertFalse(steps.get(0).dropCandidates().isEmpty(), "drop target required");
        String drop = steps.get(0).dropCandidates().get(0).value();
        Assert.assertTrue(drop.contains("done") || drop.toLowerCase().contains("done"), drop);
    }
}
