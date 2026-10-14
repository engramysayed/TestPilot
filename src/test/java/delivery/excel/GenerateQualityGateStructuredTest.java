package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class GenerateQualityGateStructuredTest {
    @Test
    public void toApiMap_hasFrontendContractKeys() {
        ManualTestCase tc = new ManualTestCase(
                "TC_01", "Empty email", "",
                "1. Open login\n2. Enter in the Email or phone field\n3. Confirm a clear validation/error state",
                "Error", "P1", "negative", "", "user@x.com", "EXECUTE", "");
        GenerateQualityGate.GateResult result = GenerateQualityGate.validateDetailed(List.of(tc), null);
        Assert.assertFalse(result.passed());
        Map<String, Object> api = GenerateQualityGate.toApiMap(result, List.of());
        Assert.assertEquals(api.get("error"), "QUALITY_GATE");
        Assert.assertEquals(api.get("code"), "QUALITY_GATE");
        Assert.assertTrue(api.get("errors") instanceof List);
        Assert.assertTrue(api.get("qualityGate") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> qg = (Map<String, Object>) api.get("qualityGate");
        Assert.assertEquals(qg.get("passed"), Boolean.FALSE);
    }
}
