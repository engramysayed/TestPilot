package delivery.agent.harness.recovery;

import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExecutionFailureClassifierTest {

    @Test
    public void locatorResolution_fromNoDomCandidate() {
        var kind = ExecutionFailureClassifier.classify(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "x", ""),
                "No DOM candidate for intent CLICK",
                "");
        Assert.assertEquals(kind, ExecutionFailureClass.LOCATOR_RESOLUTION);
    }

    @Test
    public void assertion_fromIntentKind() {
        var kind = ExecutionFailureClassifier.classify(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE, "x", ""),
                "anything",
                "");
        Assert.assertEquals(kind, ExecutionFailureClass.ASSERTION_OR_EXPECTED_BEHAVIOR);
    }
}