package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

public class HuntPlannerPromptCredsTest {
    @Test
    public void promptHasTokensNotPassword() {
        HuntPlanner.Context ctx = new HuntPlanner.Context(
                "brief", 1, 10, 10, 0, 5, "", Path.of("x.png"), List.of(),
                "", "cursor", "## map", false, "coverage", "happy", "auto",
                "data-axis-test-id", true, "alice", "245345", true);
        String prompt = OllamaHuntPlanner.buildUserPrompt(ctx);
        Assert.assertTrue(prompt.contains("TARGET_PASSWORD"), prompt);
        Assert.assertTrue(prompt.contains("TARGET_USERNAME"), prompt);
        Assert.assertFalse(prompt.contains("s3cret"));
        Assert.assertTrue(prompt.contains("login-feature=true"), prompt);
        Assert.assertTrue(prompt.contains("OTP token"), prompt);
        Assert.assertTrue(prompt.contains("TARGET_OTP"), prompt);
        Assert.assertTrue(prompt.contains("YOU log in via UI actions"), prompt);
        Assert.assertTrue(prompt.contains("iterationIndex="), prompt);
        Assert.assertTrue(prompt.contains("## Locator preference (RANK 1)"), prompt);
        Assert.assertTrue(prompt.contains("ALWAYS choose that locator"), prompt);
        Assert.assertTrue(prompt.contains("data-axis-test-id"), prompt);
    }

    @Test
    public void omitsLocatorPreferenceWhenNoProjectHooks() {
        HuntPlanner.Context ctx = new HuntPlanner.Context(
                "brief", 1, 10, 10, 0, 5, "", Path.of("x.png"), List.of(),
                "", "ollama", "## map", false, "coverage", "happy", "auto",
                "", false, "", "", false);
        String prompt = OllamaHuntPlanner.buildUserPrompt(ctx);
        Assert.assertFalse(prompt.contains("## Locator preference"), prompt);
        Assert.assertFalse(prompt.contains("No project preferred-hook"), prompt);
        Assert.assertFalse(prompt.contains("RANK 1"), prompt);
    }
}
