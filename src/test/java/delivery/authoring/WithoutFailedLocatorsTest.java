package delivery.authoring;

import delivery.heal.FailedLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class WithoutFailedLocatorsTest {

    @Test
    public void dropsFailedCssAndItsXpathTwin() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "a[href='/upstream']", "a", "banner"),
                new DomCandidate("c2", "xpath", "//a[@href='/upstream']", "a", "banner"),
                new DomCandidate("c3", "xpath",
                        "//button[contains(normalize-space(.),'Add Item')]", "button", "Add Item"));
        List<DomCandidate> left = StepIntentBinder.withoutFailedLocators(
                candidates,
                List.of(new FailedLocator("css", "a[href='/upstream']", "zero size")));
        Assert.assertEquals(left.size(), 1);
        Assert.assertEquals(left.get(0).id(), "c3");
    }
}
