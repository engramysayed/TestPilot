package delivery.store;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class LocatorMapBuilderTest {
    @Test
    public void buildsPagesAndTcSummaries() {
        ProvenStep step = new ProvenStep(
                "TC1", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        TcDraft draft = new TcDraft(
                "TC1", "t", "steps", "exp", TcDraftStatus.PASSED,
                List.of(step), List.of(), false, -1, "", "", "", 0, "https://x/cart");
        JSONObject map = LocatorMapBuilder.build(List.of(draft));
        Assert.assertTrue(map.getJSONObject("pages").has("Cart"));
        Assert.assertTrue(map.getJSONObject("tcs").has("TC1"));
        Assert.assertEquals(map.getJSONObject("summary").getInt("pageCount"), 1);
        Assert.assertFalse(map.toString().contains("\"typed\""), map.toString());
    }

    @Test
    public void omitsTypedValuesFromPackagedMap() {
        ProvenStep typed = new ProvenStep(
                "TC_CANARY", "Profile", "elementAction", "type",
                "id", "secret", "CANARY_PW_zipreplay_7f2c9a", "", "", true, "intent:TYPE");
        TcDraft draft = new TcDraft(
                "TC_CANARY", "t", "steps", "exp", TcDraftStatus.PASSED,
                List.of(typed), List.of(), false, -1, "", "", "", 0, "https://x/");
        String json = LocatorMapBuilder.build(List.of(draft)).toString();
        Assert.assertFalse(json.contains("CANARY_PW_zipreplay_7f2c9a"), json);
        Assert.assertFalse(json.contains("sampleValue"), json);
    }
}
