package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SameControlFingerprintTest {

    @Test
    public void idCssAndXpathOfSameIdAreOneControl() {
        DomCandidate id = new DomCandidate("c1", "id", "choice", "select", "choice");
        DomCandidate css = new DomCandidate("c2", "css", "select[id='choice']", "select", "choice");
        DomCandidate xpath = new DomCandidate("c3", "xpath", "//select[@id='choice']", "select", "choice");
        Assert.assertTrue(StepIntentBinder.describesSameControl(id, css));
        Assert.assertTrue(StepIntentBinder.describesSameControl(css, xpath));
        Assert.assertFalse(StepIntentBinder.describesSameControl(
                id, new DomCandidate("c9", "id", "other", "select", "other")));
    }
}
