package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HayContainsTokenTest {

    @Test
    public void tokenIsNotAPrefixOfALongerWord() {
        Assert.assertFalse(StepIntentBinder.hayContainsToken("elemental selenium", "element"));
        Assert.assertTrue(StepIntentBinder.hayContainsToken("add item", "item"));
        Assert.assertTrue(StepIntentBinder.hayContainsToken("bike-light", "bike"));
        Assert.assertTrue(StepIntentBinder.hayContainsToken("user_email", "email"));
    }
}
