package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GroundRankBatchResultTest {

    @Test
    public void consecutiveAcceptableRequiresGaplessFromZero() {
        GroundRankBatchResult result = GroundRankBatchResult.parse("""
                {"picks":[
                  {"intentIndex":0,"candidateId":"c1","confidence":"high"},
                  {"intentIndex":1,"candidateId":"c2","confidence":"medium"},
                  {"intentIndex":3,"candidateId":"c4","confidence":"high"}
                ]}""");
        Assert.assertEquals(2, result.consecutiveAcceptableFromZero().size());
    }

    @Test
    public void lowConfidenceStopsBatch() {
        GroundRankBatchResult result = GroundRankBatchResult.parse("""
                {"picks":[
                  {"intentIndex":0,"candidateId":"c1","confidence":"high"},
                  {"intentIndex":1,"candidateId":"c2","confidence":"low"}
                ]}""");
        Assert.assertEquals(1, result.consecutiveAcceptableFromZero().size());
    }
}
