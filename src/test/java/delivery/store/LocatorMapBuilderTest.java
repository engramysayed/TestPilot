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
    }
}
