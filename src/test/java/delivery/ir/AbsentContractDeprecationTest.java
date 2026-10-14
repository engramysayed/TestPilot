package delivery.ir;

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Codegen r1/07: absent "contract" + v2 locators is accepted with ONE deprecation warning per IR load. */
public class AbsentContractDeprecationTest {
    private final List<String> warnings = new ArrayList<>();
    private Consumer<String> saved;

    @BeforeMethod
    public void capture() {
        saved = ProvenStepCodec.deprecationSink;
        warnings.clear();
        ProvenStepCodec.deprecationSink = warnings::add;
    }

    @AfterMethod(alwaysRun = true)
    public void restore() {
        ProvenStepCodec.deprecationSink = saved;
    }

    static JSONObject step(String keel, boolean declare) {
        JSONObject o = new JSONObject().put("tcId", "TC_D").put("action", "click").put("locatorStrategy", "keel").put("locatorValue", keel);
        return declare ? o.put("contract", 2) : o;
    }

    @Test
    public void oneWarningPerArrayLoadNotPerStep() {
        JSONArray arr = new JSONArray().put(step("text=\"Save\"", false)).put(step("css=canvas#c >> point=100,50@800x400", false)).put(step("css=#a", false));
        Assert.assertEquals(ProvenStepCodec.fromJsonArray(arr).size(), 3);
        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).contains("2 step(s)") && warnings.get(0).contains("DEPRECATED"), warnings.get(0));
        ProvenStepCodec.fromJsonArray(arr);
        Assert.assertEquals(warnings.size(), 2, "each load warns again");
    }

    @Test
    public void oneWarningPerDraftFileAcrossProvenAndLoginSteps() {
        JSONObject draft = new JSONObject().put("tcId", "TC_D").put("status", "TODO")
                .put("provenSteps", new JSONArray().put(step("text=\"Save\"", false)))
                .put("loginSteps", new JSONArray().put(step("css=canvas#c >> provider=chartjs:bar/3", false)));
        TcDraftStore.fromJson(draft);
        Assert.assertEquals(warnings.size(), 1, warnings.toString());
        Assert.assertTrue(warnings.get(0).startsWith("DEPRECATED: TcDraft TC_D"), warnings.get(0));
    }

    @Test
    public void noWarningWhenDeclaredOrV1() {
        ProvenStepCodec.fromJsonArray(new JSONArray().put(step("text=\"Save\"", true)).put(step("css=#a", false)));
        ProvenStepCodec.fromJson(step("css=#b", false));
        Assert.assertEquals(warnings, List.of());
    }

    @Test
    public void singleStepLoadWarnsOnce() {
        ProvenStepCodec.fromJson(step("text=\"Save\"", false));
        Assert.assertEquals(warnings.size(), 1);
    }
}
