package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HuntActionSettleTest {
    @Test
    public void settleConstantIsOneSecond() {
        Assert.assertEquals(HuntActionExecutor.SETTLE_AFTER_CLICK_MS, 1000);
    }
}
