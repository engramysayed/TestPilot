package delivery.codegen;

import delivery.ir.ProvenStepCodec;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class KeelCodegenTest {
    private static final String PAY = "frame=#outer >> frame=iframe[name=\"inner\"] >> css=button";

    private static ProvenStep keelClick() {
        return new ProvenStep("TC_K", "Checkout", "elementAction", "click", "keel", PAY, "", "", "", true, "ok")
                .withLocatorCandidates(List.of(
                        new LocatorCandidate("keel", "frame=#outer >> frame=iframe[name=\"inner\"] >> css=button:nth-of-type(1)", 0.4),
                        new LocatorCandidate("keel", PAY, 0.9),
                        new LocatorCandidate("xpath", "//button", 0.95),
                        new LocatorCandidate("keel", "frame=#outer >> frame=#inner2 >> css=button", 0.7),
                        new LocatorCandidate("keel", "css=#last", 0.1)));
    }

    @Test
    public void mainFirstThenFallbacksInGivenOrderCappedAtThree() {
        ProvenStep s = keelClick();
        List<String> r = LocatorCandidate.rankedTargets(s.locatorStrategy(), s.locatorValue(), s.locatorCandidates());
        // A3 v1: main first, then fallbacks[] in the given order (not re-sorted), dup + xpath skipped, cap 3
        Assert.assertEquals(r, List.of(PAY,
                "frame=#outer >> frame=iframe[name=\"inner\"] >> css=button:nth-of-type(1)",
                "frame=#outer >> frame=#inner2 >> css=button"));
    }

    @Test
    public void legacyStrategiesBecomeCssTargetsAndSeparatorIsEscaped() {
        Assert.assertEquals(LocatorCandidate.of("id", "a\"b").toTarget(), "css=[id=\"a\\\"b\"]");
        Assert.assertEquals(LocatorCandidate.of("css", "a[title='x >> y']").toTarget(), "css=a[title='x \\>> y']");
        Assert.assertEquals(LocatorCandidate.of("data-testid", "pay").toTarget(), "css=*[data-testid='pay']");
        Assert.assertNull(LocatorCandidate.of("xpath", "//a").toTarget());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "unknown hop: role=button")
    public void unknownHopIsAHardCodegenError() {
        LocatorCandidate.of("keel", "role=button >> css=a").toTarget();
    }

    @Test
    public void codecRoundTripAndMachineFormExtrasIgnored() {
        ProvenStep s = keelClick();
        Assert.assertEquals(ProvenStepCodec.fromJson(ProvenStepCodec.toJson(s)), s);
        JSONObject o = ProvenStepCodec.toJson(new ProvenStep("T", "P", "elementAction", "click", "keel", "css=a", "", "", "", true, ""));
        Assert.assertTrue(ProvenStepCodec.toJson(s).has("locatorCandidates"));
        o.put("fallbacks", new org.json.JSONArray().put(new JSONObject(
                "{\"strategy\":\"keel\",\"value\":\"shadow=#o >> frame=#sf >> css=button\",\"score\":0.8,"
                        + "\"route\":{\"origin\":\"https://x\",\"path\":\"/pay\"},"
                        + "\"frames\":[{\"selector\":\"#sf\"}],\"shadowPath\":[],\"css\":\"button\"}")));
        Assert.assertEquals(ProvenStepCodec.fromJson(o).locatorCandidates(),
                List.of(new LocatorCandidate("keel", "shadow=#o >> frame=#sf >> css=button", 0.8)));
        // locatorCandidates wins over fallbacks when both are present
        JSONObject both = new JSONObject(o.toString());
        both.put("locatorCandidates", new org.json.JSONArray().put(new JSONObject("{\"strategy\":\"keel\",\"value\":\"css=#x\",\"score\":1}")));
        Assert.assertEquals(ProvenStepCodec.fromJson(both).locatorCandidates(), List.of(new LocatorCandidate("keel", "css=#x", 1)));
    }

    @Test
    public void emitsKeelByForContextTargetsPlainCssForCssOnlyAndCompiles() throws Exception {
        Path out = Files.createTempDirectory("codegen-keel");
        ProvenStep cssOnly = new ProvenStep("TC_K", "Checkout", "elementAction", "click", "keel",
                "css=button#continue", "", "", "", true, "ok");
        ProvenStep legacy = new ProvenStep("TC_K", "Checkout", "elementAction", "type", "id", "email", "a@b.c", "", "", true, "ok");
        ProvenStep closed = new ProvenStep("TC_K", "Checkout", "elementAction", "assert", "keel",
                "shadow-closed=#c >> css=#cb", "", "visible", "", true, "ok");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome("TC_K", "Pay", TcStatus.PASSED, List.of(cssOnly, legacy, keelClick(), closed), "", null, false, List.of())));
        Path pages = out.resolve("src/main/java/project/pages");
        String loc = Files.readString(pages.resolve("Checkout.java"));
        String act = Files.readString(pages.resolve("Checkout.java"));
        Assert.assertTrue(loc.contains("By.cssSelector(\"button#continue\")"), loc);
        Assert.assertTrue(loc.contains("By.id(\"email\")"), loc);
        Assert.assertTrue(loc.contains("import project.utils.locators.KeelBy;"), loc);
        Assert.assertTrue(loc.contains("KeelBy.of(\"") && loc.contains(
                "\"frame=#outer >> frame=iframe[name=\\\"inner\\\"] >> css=button\""), loc);
        Assert.assertTrue(loc.contains("\"shadow-closed=#c >> css=#cb\""), loc);
        Assert.assertFalse(loc.contains("//button"), loc);
        Assert.assertTrue(act.contains("SettledActions.click") || act.contains("SettledActions.type"), act);
        Assert.assertFalse(act.contains("Thread.sleep"), act);
        String settled = Files.readString(Path.of(
                "customer-framework-template/src/main/java/project/utils/locators/SettledActions.java"));
        Assert.assertTrue(settled.contains("SmartWait.untilActionable"), settled);
        Assert.assertTrue(settled.contains("KeelLocate.reset"), settled);
        Path classes = Files.createTempDirectory("codegen-keel-classes");
        CustomerTemplateCompiler.compile(classes, pages.resolve("Checkout.java")).close();
        Assert.assertTrue(Files.exists(classes.resolve("project/utils/locators/KeelLocate.class")));
    }
}
