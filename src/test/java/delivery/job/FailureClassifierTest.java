package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class FailureClassifierTest {

    @Test
    public void distinguishesAssertionLocatorProviderAndInfrastructure() {
        Assert.assertEquals(FailureClassifier.suggest("Assert failed: welcome"), FailureClassifier.Kind.ASSERTION);
        Assert.assertEquals(FailureClassifier.suggest("heal_exhausted no locator"), FailureClassifier.Kind.LOCATOR);
        Assert.assertEquals(FailureClassifier.suggest("ollama not on allowlist"), FailureClassifier.Kind.PROVIDER);
        Assert.assertEquals(FailureClassifier.suggest("Chrome not reachable"), FailureClassifier.Kind.INFRASTRUCTURE);
    }

    @Test
    public void userCorrectionDoesNotEraseSuggestion() {
        FailureClassifier.Classification c = FailureClassifier.apply(
                "heal_exhausted", FailureClassifier.Kind.ASSERTION);
        Assert.assertEquals(c.suggested(), FailureClassifier.Kind.LOCATOR);
        Assert.assertEquals(c.effective(), FailureClassifier.Kind.ASSERTION);
        Assert.assertTrue(c.userCorrected());
    }
}
