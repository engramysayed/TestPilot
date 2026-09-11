package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GroundRankResultTest {

    @Test
    public void parseHighConfidence() {
        GroundRankResult result = GroundRankResult.parse(
                "{\"candidateId\":\"c3\",\"confidence\":\"high\",\"rationale\":\"Sign In\"}");
        Assert.assertEquals("c3", result.candidateId());
        Assert.assertTrue(result.isAcceptable());
    }

    @Test
    public void lowConfidenceNotAcceptable() {
        GroundRankResult result = GroundRankResult.parse(
                "{\"candidateId\":\"c1\",\"confidence\":\"low\"}");
        Assert.assertFalse(result.isAcceptable());
    }

    @Test
    public void invalidJsonDefaultsLow() {
        GroundRankResult result = GroundRankResult.parse("not json");
        Assert.assertFalse(result.isAcceptable());
    }
}
