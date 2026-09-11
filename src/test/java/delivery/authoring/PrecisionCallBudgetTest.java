package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PrecisionCallBudgetTest {

    @Test
    public void capExhaustsOnFourthTryWhenCapIsThree() {
        PrecisionCallBudget budget = new PrecisionCallBudget(3);
        Assert.assertTrue(budget.tryConsume());
        Assert.assertTrue(budget.tryConsume());
        Assert.assertTrue(budget.tryConsume());
        Assert.assertFalse(budget.tryConsume());
        Assert.assertEquals(3, budget.used());
        Assert.assertEquals(0, budget.remaining());
    }

    @Test
    public void disabledPrecisionUsesZeroCap() {
        PrecisionCallBudget budget = PrecisionCallBudget.fromProperties(false, 50);
        Assert.assertFalse(budget.tryConsume());
        Assert.assertEquals(0, budget.cap());
    }
}
