package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PrecisionJobConfigTest {

    @Test
    public void negativeMaxCallsDefaultsToFifty() {
        PrecisionJobConfig config = new PrecisionJobConfig(true, -1);
        Assert.assertEquals(50, config.maxCallsPerJob());
    }

    @Test
    public void defaultsDoNotReuseLocatorMemory() {
        Assert.assertFalse(PrecisionJobConfig.DEFAULTS.reuseLocatorMemory());
    }
}
