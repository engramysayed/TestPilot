package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

/** Documents the open-then-hunter-login contract for Bug Hunter. */
public class LiveHuntOpenUrlTest {

    @Test
    public void openThenLoginHelperOrdersBaseUrlFirst() {
        Assert.assertEquals(
                LiveHuntService.openThenLoginOrderHint(),
                "baseUrl-then-hunter-login");
    }

    @Test
    public void systemPromptSaysHunterDrivesLoginNotServer() {
        String sys = OllamaHuntPlanner.systemPrompt();
        Assert.assertTrue(sys.contains("never auto-logs in"), sys);
        Assert.assertFalse(sys.toLowerCase().contains("soft-logs in again"), sys);
    }
}
