package delivery.vision;

import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

public class QwenVisionPromptContractTest {

    @Test
    public void systemPromptForbidsXpathAndIsLocateOnly() {
        String system = VisionPromptTemplate.systemPrompt();
        Assert.assertTrue(system.toLowerCase().contains("do not generate xpath"), system);
        Assert.assertTrue(system.contains("found"), system);
        Assert.assertTrue(system.contains("bbox"), system);
        Assert.assertEquals(VisionPromptTemplate.version(), "v1");
    }

    @Test
    public void userPromptContainsActionAndTarget() {
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Checkout button");
        String user = VisionPromptTemplate.userPrompt(intent);
        Assert.assertTrue(user.contains("ACTION:"), user);
        Assert.assertTrue(user.contains("CLICK"), user);
        Assert.assertTrue(user.contains("TARGET:"), user);
        Assert.assertTrue(user.contains("Click the Checkout button"), user);
        Assert.assertTrue(user.contains("SEMANTIC CONSTRAINTS:"), user);
    }

    @Test
    public void qwenSystemPromptUsesTemplate() {
        Assert.assertTrue(QwenVisionProvider.SYSTEM_PROMPT.toLowerCase().contains("do not generate xpath"));
        Assert.assertTrue(QwenVisionProvider.SYSTEM_PROMPT.contains("bbox"));
    }
}
