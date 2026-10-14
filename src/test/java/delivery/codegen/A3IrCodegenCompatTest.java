package delivery.codegen;

import delivery.ir.ProvenStepCodec;
import delivery.snapshot.RankedLocators;
import delivery.snapshot.ScoredLocator;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Real-class compat (no mirror): A3 RankedLocators.toIrJson -> codegen ProvenStepCodec ->
 * LocatorCandidate.rankedTargets. Same fixture as A3's CodegenCompatTest.
 */
public class A3IrCodegenCompatTest {
    private static ProvenStep stepFrom(JSONObject ir) {
        JSONObject o = new JSONObject(ir.toString());
        o.put("tcId", "T").put("pageName", "Orders").put("action", "click").put("validated", true);
        return ProvenStepCodec.fromJson(o);
    }

    @Test
    public void a3IrGivesMainThenFallbacksWithoutDuplicatingMain() {
        RankedLocators r = new RankedLocators(
                new ScoredLocator("keel", "css=button[data-testid=\"save\"]", 0.95, "testid"),
                List.of(new ScoredLocator("keel", "css=button[data-axis-test-id=\"save-hook\"]", 1.0, "hook"),
                        new ScoredLocator("keel", "css=button[aria-label=\"Save order\"]", 0.85, "role+name")),
                "h/orders/:id");
        JSONObject ir = r.toIrJson();
        Assert.assertTrue(ir.has("fallbacks") && ir.has("route"), ir.toString());
        ProvenStep s = stepFrom(ir);
        Assert.assertEquals(s.locatorStrategy(), "keel");
        Assert.assertEquals(s.locatorCandidates().size(), ir.getJSONArray("locatorCandidates").length());
        List<String> ranked = LocatorCandidate.rankedTargets(s.locatorStrategy(), s.locatorValue(), s.locatorCandidates());
        Assert.assertEquals(ranked, List.of(
                "css=button[data-testid=\"save\"]",
                "css=button[data-axis-test-id=\"save-hook\"]",
                "css=button[aria-label=\"Save order\"]"));
        Assert.assertEquals(PageAccumulator.rankedFor(s), ranked);
    }

    @Test
    public void withProvenReorderAndContextPrefixSurviveCodegen() {
        RankedLocators r = RankedLocators.of(List.of(
                new ScoredLocator("keel", "shadow-closed=#c >> css=[id=\"cb\"]", 0.60, "css-id"),
                new ScoredLocator("keel", "shadow-closed=#c >> css=#cb", 0.40, "serializer")), "h/x")
                .withProven("shadow-closed=#c >> css=#cb");
        ProvenStep s = stepFrom(r.toIrJson());
        Assert.assertEquals(LocatorCandidate.rankedTargets(s.locatorStrategy(), s.locatorValue(), s.locatorCandidates()),
                List.of("shadow-closed=#c >> css=#cb", "shadow-closed=#c >> css=[id=\"cb\"]"));
        PageAccumulator pages = new PageAccumulator();
        pages.add(s);
        String expr = pages.pages().values().iterator().next().fields().get(0).smartExpr();
        Assert.assertEquals(expr.split("\n").length, 3, expr);   // KeelBy.of(label, 2 targets): main not duplicated
    }
}
