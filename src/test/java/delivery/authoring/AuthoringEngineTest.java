package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AuthoringEngineTest {

    @Test
    public void parseDefaultsToKeel() {
        Assert.assertEquals(AuthoringEngine.KEEL, AuthoringEngine.parse(null));
        Assert.assertEquals(AuthoringEngine.KEEL, AuthoringEngine.parse(""));
        Assert.assertEquals(AuthoringEngine.KEEL, AuthoringEngine.parse("unknown"));
        Assert.assertEquals(AuthoringEngine.KEEL, AuthoringEngine.parse("keel"));
        Assert.assertEquals(AuthoringEngine.PRECISION, AuthoringEngine.parse("precision"));
    }

    @Test
    public void wireValueRoundTrips() {
        Assert.assertEquals("keel", AuthoringEngine.KEEL.wireValue());
        Assert.assertEquals("precision", AuthoringEngine.PRECISION.wireValue());
    }
}
