package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LocatorPreferenceTest {
    @Test
    public void submitButtonBeatsBareFormId() {
        Assert.assertTrue(LocatorPreference.score("cssSelector", "button[type='submit']")
                > LocatorPreference.score("id", "login"));
        Assert.assertTrue(LocatorPreference.prefer(
                "cssSelector", "button[type='submit']", "id", "login"));
    }
}
