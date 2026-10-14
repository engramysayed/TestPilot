package delivery.hunt;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class HuntUrlStrategyBudgetTest {
    @Test public void plateauAfterBudgetWithoutNewUrls() {
        HuntUrlStrategyBudget b = new HuntUrlStrategyBudget(2);
        b.noteCycle("http://x/app?q=1", 1);
        assertFalse(b.plateauAfterCycle("http://x/app", 1, 1));
        b.noteCycle("http://x/app#frag", 1);
        assertTrue(b.plateauAfterCycle("http://x/app/", 1, 1));
    }
}
