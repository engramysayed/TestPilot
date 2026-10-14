package delivery.codegen;

import delivery.ir.ProvenStepCodec;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.snapshot.RankedLocators;
import delivery.snapshot.ScoredLocator;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class KeelCodegenV2Test {
    private static ProvenStep keel(String action, String main, List<LocatorCandidate> cands) {
        return new ProvenStep("TC_V2", "Board", "elementAction", action, "keel", main, "", "", "", true, "ok")
                .withLocatorCandidates(cands);
    }

    @Test
    public void irWithNewerContractIsRejectedClearly() {
        JSONObject o = ProvenStepCodec.toJson(keel("click", "css=#a", List.of()));
        o.put("contract", 3);
        IllegalArgumentException e = Assert.expectThrows(IllegalArgumentException.class, () -> ProvenStepCodec.fromJson(o));
        Assert.assertTrue(e.getMessage().contains("contract 3") && e.getMessage().contains("supports up to 2"), e.getMessage());
        o.put("contract", 2);
        Assert.assertEquals(ProvenStepCodec.fromJson(o).locatorValue(), "css=#a");
    }

    @Test
    public void contractFieldOnlyWhenNeeded() {
        Assert.assertFalse(ProvenStepCodec.toJson(keel("click", "css=#a", List.of())).has("contract"));
        Assert.assertEquals(ProvenStepCodec.toJson(keel("click", "text=\"Refresh\"", List.of())).getInt("contract"), 2);
    }

    @Test
    public void engineV2IrRoundTripsThroughCodegen() {
        RankedLocators r = new RankedLocators(
                new ScoredLocator("keel", "text=\"Refresh\"", 0.65, "text"),
                List.of(new ScoredLocator("keel", "css=button.btn", 0.40, "class")), "h/x");
        JSONObject ir = r.toIrJson();
        Assert.assertEquals(ir.getInt("contract"), 2);
        ir.put("tcId", "T").put("pageName", "P").put("action", "click");
        ProvenStep s = ProvenStepCodec.fromJson(ir);
        Assert.assertEquals(PageAccumulator.rankedFor(s), List.of("text=\"Refresh\"", "css=button.btn"));
    }

    @Test
    public void piiTextFallbacksAreDroppedAndPiiOnlyMainFails() {
        ProvenStep s = keel("click", "css=#x", List.of(
                LocatorCandidate.of("keel", "text=\"Hello, Sayed\""), LocatorCandidate.of("keel", "text=\"Save\"")));
        Assert.assertEquals(PageAccumulator.rankedFor(s), List.of("css=#x", "text=\"Save\""));
        PageAccumulator pages = new PageAccumulator();
        IllegalStateException e = Assert.expectThrows(IllegalStateException.class,
                () -> pages.add(keel("click", "text=\"john.doe@example.com\"", List.of())));
        Assert.assertTrue(e.getMessage().contains("PII"), e.getMessage());
    }

    @Test
    public void emitsTextPointerAndDragAndCompiles() throws Exception {
        Path out = Files.createTempDirectory("codegen-v2");
        ProvenStep text = keel("click", "shadow-closed=#c >> text=\"Closed action\"",
                List.of(LocatorCandidate.of("keel", "shadow-closed=#c >> css=#cb")));
        ProvenStep bar = keel("click", "css=canvas#chart >> provider=chartjs:bar/3 >> point=100,50@800x400", List.of());
        JSONObject dragIr = ProvenStepCodec.toJson(keel("", "css=#card-1", List.of()));
        dragIr.put("actionHint", "drag").put("dropTarget", "css=#lane-done >> point=50,10@200x400")
                .put("route", "h/board").put("fallbacks", new org.json.JSONArray());
        ProvenStep drag = ProvenStepCodec.fromJson(dragIr);
        Assert.assertTrue(drag.isDrag());
        Assert.assertEquals(ProvenStepCodec.fromJson(ProvenStepCodec.toJson(drag)), drag, "drag round trip");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome("TC_V2", "Board", TcStatus.PASSED, List.of(text, bar, drag), "", null, false, List.of())));
        Path pages = out.resolve("src/main/java/project/pages");
        String loc = Files.readString(pages.resolve("Board_Locators.java"));
        String act = Files.readString(pages.resolve("Board_Actions.java"));
        Assert.assertTrue(loc.contains("KeelLocate.requireContract(2);"), loc);
        Assert.assertTrue(loc.contains("\"shadow-closed=#c >> text=\\\"Closed action\\\"\""), loc);
        Assert.assertTrue(loc.contains("\"css=canvas#chart >> provider=chartjs:bar/3 >> point=100,50@800x400\""), loc);
        Assert.assertTrue(loc.contains("\"css=#lane-done >> point=50,10@200x400\""), loc);
        Assert.assertTrue(act.contains("KeelActions.click(driver.get(), "), act);
        Assert.assertTrue(act.contains("KeelActions.drag(driver.get(), "), act);
        Assert.assertTrue(act.contains("driver.element().click("), act);   // plain text target keeps normal click
        Path classes = Files.createTempDirectory("codegen-v2-classes");
        CustomerTemplateCompiler.compile(classes, pages.resolve("Board_Locators.java"), pages.resolve("Board_Actions.java")).close();
        Assert.assertTrue(Files.exists(classes.resolve("project/utils/locators/KeelActions.class")));
    }

    @Test
    public void v1OnlyOutputHasNoContractGuard() throws Exception {
        Path out = Files.createTempDirectory("codegen-v1");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome("TC_V1", "P", TcStatus.PASSED, List.of(keel("click", "frame=#f >> css=#b", List.of())), "", null, false, List.of())));
        Path locFile;
        try (var files = Files.list(out.resolve("src/main/java/project/pages"))) {
            locFile = files.filter(f -> f.toString().endsWith("_Locators.java")).findFirst().orElseThrow();
        }
        String loc = Files.readString(locFile);
        Assert.assertFalse(loc.contains("requireContract"), loc);
        Assert.assertTrue(loc.contains("contract v1"), loc);
    }
}
