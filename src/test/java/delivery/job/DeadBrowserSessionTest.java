package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DeadBrowserSessionTest {

    @Test
    public void detectsClosedWindowMessages() {
        Assert.assertTrue(DeadBrowserSession.isDead(
                "no such window: target window already closed from unknown error: web view not found"));
        Assert.assertTrue(DeadBrowserSession.isDead("invalid session id"));
        Assert.assertFalse(DeadBrowserSession.isDead("element not visible within timeout"));
        Assert.assertFalse(DeadBrowserSession.isDead((String) null));
        Assert.assertFalse(DeadBrowserSession.isDead(""));
    }
}
