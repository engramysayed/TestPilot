package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HuntFeatureHintsTest {
    @Test
    public void detectsLoginFeatureAndUrl() {
        Assert.assertTrue(HuntFeatureHints.looksLikeLoginFeature(
                "https://x/login", "testing the login function", ""));
        Assert.assertTrue(HuntFeatureHints.looksLikeLoginUrl("https://opssit.axispay.app/login?x=1"));
    }

    @Test
    public void advancesHappyWhenStuckOnLogin() {
        Assert.assertTrue(HuntFeatureHints.shouldAdvanceHappy("happy", 2,
                "https://ex/login", true, false));
        Assert.assertFalse(HuntFeatureHints.shouldAdvanceHappy("happy", 1,
                "https://ex/login", true, false));
        Assert.assertFalse(HuntFeatureHints.shouldAdvanceHappy("happy", 3,
                "https://ex/login", true, true));
    }
}
