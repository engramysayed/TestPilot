package delivery.snapshot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Pins the IR contract with Keel Codegen A2 (testpilot-codegen/patches/A2/02-keel-codegen.diff):
 * ProvenStepCodec.candidatesFrom reads locatorCandidates[{strategy,value,score}] and
 * LocatorCandidate.rankedTargets puts the proven locator first, then the rest by score, deduped by
 * KeelTarget.format(), capped at 3. Both are mirrored here (codegen classes are not in this tree).
 */
public class CodegenCompatTest {
    private static RankedLocators sample() {
        return new RankedLocators(
                new ScoredLocator("keel", "shadow-closed=#c >> css=[id=\"cb\"]", 0.60, "css-id"),
                List.of(new ScoredLocator("keel", "shadow-closed=#c >> css=#cb", 0.40, "serializer")),
                "h/orders/:id").withProven("shadow-closed=#c >> css=[id=\"cb\"]");
    }

    @Test public void irJsonReadsBackThroughCodegenCodecAndRanking() {
        RankedLocators r = new RankedLocators(
                new ScoredLocator("keel", "css=button[data-testid=\"save\"]", 0.95, "testid"),
                List.of(new ScoredLocator("keel", "css=button[data-axis-test-id=\"save-hook\"]", 1.0, "hook"),
                        new ScoredLocator("keel", "css=button[aria-label=\"Save order\"]", 0.85, "role+name")),
                "h/orders/:id");
        JSONObject ir = r.toIrJson();
        assertEquals(ir.getString("locatorStrategy"), "keel");
        // codec mirror
        JSONArray arr = ir.getJSONArray("locatorCandidates");
        List<String[]> cands = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.getJSONObject(i);
            cands.add(new String[]{j.optString("strategy", ""), j.optString("value", ""), String.valueOf(j.optDouble("score", 0))});
        }
        // rankedTargets mirror: proven first, then by score desc, dedupe by canonical target, cap 3
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(KeelTarget.parse(ir.getString("locatorValue")).format());
        cands.sort(Comparator.comparingDouble((String[] c) -> Double.parseDouble(c[2])).reversed());
        for (String[] c : cands) { if (out.size() >= 3) break; assertEquals(c[0], "keel"); out.add(KeelTarget.parse(c[1]).format()); }
        assertEquals(new ArrayList<>(out), List.of(
                "css=button[data-testid=\"save\"]",                // proven main stays first
                "css=button[data-axis-test-id=\"save-hook\"]",
                "css=button[aria-label=\"Save order\"]"));
        assertEquals(ir.getString("route"), "h/orders/:id");
    }

    @Test public void everyValueIsAValidContractV1Target() {
        for (ScoredLocator l : sample().all()) assertEquals(KeelTarget.parse(l.value()).format(), l.value());
    }
}
