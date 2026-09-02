package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

public class VisionAssertionEchoGateTest {

    @Test
    public void realObservationWithExtraDetailIsNotEcho() {
        String claim = "Login page shows username and password fields";
        String obs = "Username and password fields are visible on the login page.";
        Assert.assertFalse(VisionAssertionGate.echoesAssertion(obs, claim));
    }

    @Test
    public void exactRestateIsEcho() {
        String claim = "Login page shows username and password fields";
        Assert.assertTrue(VisionAssertionGate.echoesAssertion(claim, claim));
    }
}
