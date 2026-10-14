package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Codegen marks assertion-only cases @KeelReadOnly + KeelRetryAnalyzer; cases with actions are not marked. */
public class KeelReadOnlyCodegenTest {
    private static ProvenStep assertVisible() {
        return new ProvenStep("TC_RO", "Home", "assertion", "assert", "keel", "css=h1", "", "visible", "", true, "ok");
    }

    private static ProvenStep click() {
        return new ProvenStep("TC_RW", "Home", "elementAction", "click", "keel", "css=#save", "", "", "", true, "ok");
    }

    private static String generatedTest(Path out) throws Exception {
        try (var s = Files.walk(out.resolve("src/test/java"))) {
            return Files.readString(s.filter(f -> f.toString().endsWith(".java")).findFirst().orElseThrow());
        }
    }

    @Test
    public void assertionOnlyCaseIsReadOnlyAndCompiles() throws Exception {
        Path out = Files.createTempDirectory("ro");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome("TC_RO", "Home", TcStatus.PASSED, List.of(assertVisible()), "", null, false, List.of())));
        String test = generatedTest(out);
        Assert.assertTrue(test.contains("@KeelReadOnly"), test);
        Assert.assertTrue(test.contains("retryAnalyzer = KeelRetryAnalyzer.class"), test);
        List<Path> srcs = new java.util.ArrayList<>();
        try (var s = Files.walk(out.resolve("src"))) {
            s.filter(f -> f.toString().endsWith(".java")).forEach(srcs::add);
        }
        // BaseTest minus TestNGListeners (its Allure environment writer is not on this build's classpath)
        Path base = Files.createTempDirectory("ro-base").resolve("BaseTest.java");
        Files.writeString(base, Files.readString(Path.of("customer-framework-template/src/test/java/project/tests/BaseTest.java"))
                .replace("project.listeners.TestNGListeners.class, ", ""));
        srcs.add(base);
        CustomerTemplateCompiler.compile(Files.createTempDirectory("ro-classes"), srcs.toArray(Path[]::new)).close();
    }

    @Test
    public void caseWithActionsIsNotReadOnly() throws Exception {
        Path out = Files.createTempDirectory("rw");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome("TC_RW", "Home", TcStatus.PASSED, List.of(click(), assertVisible()), "", null, false, List.of())));
        String test = generatedTest(out);
        Assert.assertFalse(test.contains("KeelReadOnly"), test);
        Assert.assertFalse(test.contains("retryAnalyzer"), test);
    }

    @Test
    public void readOnlyRule() {
        Map<String, Object> a = Map.of("kind", "assert");
        Map<String, Object> act = Map.of("kind", "action");
        Assert.assertTrue(CodeWriter.isReadOnlyCase(List.of(a), List.of()));
        Assert.assertFalse(CodeWriter.isReadOnlyCase(List.of(), List.of()));
        Assert.assertFalse(CodeWriter.isReadOnlyCase(List.of(a), List.of(act)), "mutating setup");
        Assert.assertFalse(CodeWriter.isReadOnlyCase(List.of(a, act), List.of()));
    }

    private static String generate(String tc, List<ProvenStep> steps) throws Exception {
        Path out = Files.createTempDirectory("se");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome(tc, "Home", TcStatus.PASSED, steps, "", null, false, List.of())));
        return generatedTest(out);
    }

    @Test
    public void actionsMarkedSideEffectsNoneMakeTheCaseReadOnly() throws Exception {
        ProvenStep openTab = click().withSideEffects(ProvenStep.SIDE_EFFECTS_NONE);
        String test = generate("TC_RW", List.of(openTab, assertVisible()));
        Assert.assertTrue(test.contains("@KeelReadOnly") && test.contains("retryAnalyzer = KeelRetryAnalyzer.class"), test);
    }

    @Test
    public void oneMutatingOrUnknownActionKeepsTheCaseRetryFree() throws Exception {
        ProvenStep none = click().withSideEffects(ProvenStep.SIDE_EFFECTS_NONE);
        ProvenStep save = new ProvenStep("TC_RW", "Home", "elementAction", "click", "keel", "css=#save2", "", "", "", true, "ok");
        Assert.assertFalse(generate("TC_RW", List.of(none, save.withSideEffects("mutating"))).contains("KeelReadOnly"));
        Assert.assertFalse(generate("TC_RW", List.of(none, save)).contains("KeelReadOnly"), "absent marker == unknown");
    }

    @Test
    public void sideEffectsRoundTripsThroughIrAndIsAbsentWhenUnknown() {
        org.json.JSONObject j = delivery.ir.ProvenStepCodec.toJson(click().withSideEffects("none"));
        Assert.assertEquals(j.getString("sideEffects"), "none");
        Assert.assertEquals(delivery.ir.ProvenStepCodec.fromJson(j).sideEffects(), "none");
        org.json.JSONObject plain = delivery.ir.ProvenStepCodec.toJson(click());
        Assert.assertFalse(plain.has("sideEffects"), "v1 IR stays byte-identical");
        Assert.assertEquals(delivery.ir.ProvenStepCodec.fromJson(plain).sideEffects(), "unknown");
        plain.put("sideEffects", org.json.JSONObject.NULL);
        Assert.assertEquals(delivery.ir.ProvenStepCodec.fromJson(plain).sideEffects(), "unknown");
        plain.put("sideEffects", "readonly");
        IllegalArgumentException e = Assert.expectThrows(IllegalArgumentException.class, () -> delivery.ir.ProvenStepCodec.fromJson(plain));
        Assert.assertTrue(e.getMessage().contains("none|mutating|unknown"), e.getMessage());
    }

    @Test
    public void readOnlyRuleWithMarker() {
        Map<String, Object> a = Map.of("kind", "assert");
        Map<String, Object> none = Map.of("kind", "action", "sideEffects", "none");
        Map<String, Object> unknown = Map.of("kind", "action", "sideEffects", "unknown");
        Assert.assertTrue(CodeWriter.isReadOnlyCase(List.of(none), List.of()));
        Assert.assertTrue(CodeWriter.isReadOnlyCase(List.of(none, a), List.of(none)));
        Assert.assertFalse(CodeWriter.isReadOnlyCase(List.of(none, unknown), List.of()));
        Assert.assertFalse(CodeWriter.isReadOnlyCase(List.of(a), List.of(unknown)), "mutating setup");
    }
}
