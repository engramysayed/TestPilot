package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TcGenerateServiceEmptyBatchTest {
    @Test
    public void emptyTestCasesJsonIsBrokenBatch() {
        Assert.assertTrue(TcGenerateService.looksLikeEmptyOrBrokenBatch(
                "{\"testCases\":[],\"coverageNotes\":\"none\"}"));
    }

    @Test
    public void validBatchIsNotBroken() {
        Assert.assertFalse(TcGenerateService.looksLikeEmptyOrBrokenBatch("""
                {
                  "testCases": [
                    {
                      "tcId": "TC_01",
                      "title": "Login",
                      "preconditions": "",
                      "steps": "1. Open /login",
                      "expectedResult": "1. Shown",
                      "priority": "P1",
                      "tags": "",
                      "visualAssertion": "",
                      "testData": "",
                      "keelPath": "AUTOMATE"
                    }
                  ],
                  "coverageNotes": "ok"
                }
                """));
    }

    @Test
    public void blankRawIsBroken() {
        Assert.assertTrue(TcGenerateService.looksLikeEmptyOrBrokenBatch(""));
        Assert.assertTrue(TcGenerateService.looksLikeEmptyOrBrokenBatch(null));
    }
}
