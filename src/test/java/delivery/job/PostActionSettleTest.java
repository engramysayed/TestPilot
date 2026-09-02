package delivery.job;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class PostActionSettleTest {

    @AfterMethod
    public void clearOverride() {
        System.clearProperty(PostActionSettle.PROPERTY);
    }

    @Test
    public void defaultWaitIsFiveSeconds() {
        System.clearProperty(PostActionSettle.PROPERTY);
        Assert.assertEquals(PostActionSettle.waitMs(), 5000L);
    }

    @Test
    public void propertyOverrideIsHonored() {
        System.setProperty(PostActionSettle.PROPERTY, "1000");
        Assert.assertEquals(PostActionSettle.waitMs(), 1000L);
    }

    @Test
    public void zeroDisablesSleep() {
        System.setProperty(PostActionSettle.PROPERTY, "0");
        Assert.assertEquals(PostActionSettle.waitMs(), 0L);
    }

    @Test
    public void negativeClampedToZero() {
        System.setProperty(PostActionSettle.PROPERTY, "-5");
        Assert.assertEquals(PostActionSettle.waitMs(), 0L);
    }
}
