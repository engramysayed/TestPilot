package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

public class VisionAssertionParserTest {

    @Test
    public void parsePass() {
        VisionAssertionResult r = VisionAssertionParser.parse("""
                {"status":"PASS","confidence":0.91,"observation":"Welcome visible","evidence":"heading"}
                """);
        Assert.assertEquals(r.status(), VisionAssertionStatus.PASS);
        Assert.assertEquals(r.confidence(), 0.91, 0.001);
        Assert.assertEquals(r.observation(), "Welcome visible");
        Assert.assertEquals(r.evidence(), "heading");
        Assert.assertNull(r.error());
    }

    @Test
    public void parseFail() {
        VisionAssertionResult r = VisionAssertionParser.parse("""
                {"status":"FAIL","confidence":0.8,"observation":"no heading","evidence":"blank page"}
                """);
        Assert.assertEquals(r.status(), VisionAssertionStatus.FAIL);
    }

    @Test
    public void missingStatusIsUncertain() {
        VisionAssertionResult r = VisionAssertionParser.parse("""
                {"confidence":0.9,"observation":"x","evidence":"y"}
                """);
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
    }

    @Test
    public void unknownStatusIsUncertain() {
        VisionAssertionResult r = VisionAssertionParser.parse("""
                {"status":"MAYBE","confidence":0.9,"observation":"x","evidence":"y"}
                """);
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
    }

    @Test
    public void emptyResponseIsUncertainNeverPass() {
        VisionAssertionResult r = VisionAssertionParser.parse("");
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
        Assert.assertNotNull(r.error());
    }

    @Test
    public void missingConfidenceDefaultsToHalf() {
        VisionAssertionResult r = VisionAssertionParser.parse("""
                {"status":"PASS","observation":"ok","evidence":"ui"}
                """);
        Assert.assertEquals(r.confidence(), 0.5, 0.001);
        Assert.assertEquals(r.status(), VisionAssertionStatus.PASS);
    }

    @Test
    public void negativeConfidenceDefaultsToHalf() {
        VisionAssertionResult r = VisionAssertionParser.parse("""
                {"status":"FAIL","confidence":-1,"observation":"x","evidence":"y"}
                """);
        Assert.assertEquals(r.confidence(), 0.5, 0.001);
    }
}
