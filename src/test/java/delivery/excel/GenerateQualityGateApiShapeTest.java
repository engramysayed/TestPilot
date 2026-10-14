package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Backend API gaps: structured QUALITY_GATE body shape (errors[], suggestions[], qualityGate.failures).
 * String detail/message remain for back-compat.
 */
public class GenerateQualityGateApiShapeTest {
    @Test
    public void qualityGateBody_hasErrorsSuggestionsAndFailuresHowToFix() {
        ManualTestCase shallow = new ManualTestCase(
                "TC_01", "Empty email", "",
                "1. Open login\n2. Enter in the Email or phone field\n3. Confirm a clear validation/error state",
                "Error", "P1", "negative", "", "user@x.com", "EXECUTE", "");
        GenerateQualityGate.GateResult result = GenerateQualityGate.validateDetailed(List.of(shallow), null);
        Assert.assertFalse(result.passed(), "shallow case should fail gate");

        Map<String, Object> suggestion = Map.of("keelPath", "AUTOMATE", "technique", "boundary");
        Map<String, Object> api = GenerateQualityGate.toApiMap(result, List.of(suggestion));

        Assert.assertEquals(api.get("code"), "QUALITY_GATE");
        Assert.assertEquals(api.get("error"), "QUALITY_GATE");
        Assert.assertTrue(api.containsKey("message"));
        Assert.assertTrue(api.containsKey("detail"));
        Assert.assertTrue(api.get("errors") instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) api.get("errors");
        Assert.assertFalse(errors.isEmpty());
        Map<String, Object> err0 = errors.get(0);
        Assert.assertTrue(err0.containsKey("rule"));
        Assert.assertTrue(err0.containsKey("message"));
        Assert.assertTrue(err0.containsKey("severity"));

        Assert.assertTrue(api.get("suggestions") instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) api.get("suggestions");
        Assert.assertEquals(suggestions.get(0).get("keelPath"), "AUTOMATE");

        @SuppressWarnings("unchecked")
        Map<String, Object> qg = (Map<String, Object>) api.get("qualityGate");
        Assert.assertEquals(qg.get("passed"), Boolean.FALSE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) qg.get("failures");
        Assert.assertFalse(failures.isEmpty());
        Assert.assertTrue(failures.get(0).containsKey("code"));
        Assert.assertTrue(failures.get(0).containsKey("message"));
        Assert.assertTrue(failures.get(0).containsKey("howToFix"));
    }
}
