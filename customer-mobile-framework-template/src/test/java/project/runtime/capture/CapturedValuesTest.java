package project.runtime.capture;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class CapturedValuesTest {

    @AfterMethod
    public void clear() {
        CapturedValues.clear();
    }

    @Test
    public void putGetRoundTripAndClear() {
        CapturedValues.put("orderId", "ORD-1");
        Assert.assertEquals(CapturedValues.get("orderId"), "ORD-1");
        CapturedValues.clear();
        try {
            CapturedValues.get("orderId");
            Assert.fail("expected missing after clear");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("orderId"));
        }
    }

    @Test
    public void perLeafIndependenceViaClearBetweenInvocations() {
        CapturedValues.put("x", "leaf-a");
        CapturedValues.clear();
        CapturedValues.put("x", "leaf-b");
        Assert.assertEquals(CapturedValues.get("x"), "leaf-b");
    }
}
