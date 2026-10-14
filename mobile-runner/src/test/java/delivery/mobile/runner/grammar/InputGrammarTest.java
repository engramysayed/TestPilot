package delivery.mobile.runner.grammar;

import delivery.mobile.ir.MobileContext;
import delivery.mobile.ir.MobileInput;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;
import delivery.mobile.ir.MobileStepEvidence;
import delivery.mobile.ir.MobileTarget;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.*;

public class InputGrammarTest {

    private static MobileStep tap(String id) {
        return new MobileStep(id, "login", MobileOperation.TAP,
                new MobileContext("NATIVE", "com.example.test", null, null),
                new MobileTarget("accessibilityId", "x", "USER", 1),
                null, null, null, false, new MobileStepEvidence(null, List.of()),
                null, null, null, null, null, null);
    }

    private static MobileStep typeSecret(String id, String key) {
        return new MobileStep(id, "login", MobileOperation.TYPE,
                new MobileContext("NATIVE", "com.example.test", null, null),
                new MobileTarget("resourceId", "pwd", "USER", 1),
                new MobileInput("SECRET_REF", key, null, true),
                null, null, false, new MobileStepEvidence(null, List.of()),
                null, null, null, null, null, null);
    }

    @Test
    public void quotesAndUnicodePreserved() {
        List<String> toks = QuotedStringLexer.tokenize("Tap \"Sign in\" on 'تسجيل'");
        assertEquals(toks.get(0), "Tap");
        assertEquals(toks.get(1), "Sign in");
        assertEquals(toks.get(2), "on");
        assertEquals(toks.get(3), "تسجيل");
    }

    @Test
    public void duplicateOverrideConflicts() {
        String sheet = """
                TC_ID,Step,Context,Strategy,Value
                tc1,s1,NATIVE,resourceId,a
                tc1,s1,NATIVE,resourceId,b
                """;
        var r = new LocatorOverrideSheetParser().parse(sheet);
        assertTrue(r.errors().stream().anyMatch(e -> e.startsWith("OVERRIDE_DUPLICATE")));
        assertEquals(r.overrides().size(), 1);
    }

    @Test
    public void reorderedStepsDoNotRebindOverrides() {
        var overrides = new LocatorOverrideSheetParser().parse("""
                TC_ID,Step,Context,Strategy,Value
                tc1,step-b,NATIVE,text,B
                """).overrides();
        // steps reordered: step-a then step-b — override still keyed by step-b
        List<MobileStep> steps = List.of(tap("step-a"), tap("step-b"));
        var v = new InputGrammarValidator().validate(steps, overrides, Set.of());
        assertTrue(v.ok());
        assertEquals(overrides.get(0).stepId(), "step-b");
        assertEquals(overrides.get(0).value(), "B");
    }

    @Test
    public void requiredSecretRefs() {
        List<MobileStep> steps = List.of(tap("s1"));
        var v = new InputGrammarValidator().validate(steps, List.of(), Set.of("auth.phone"));
        assertFalse(v.ok());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("REQUIRED_SECRET_REF")));
        steps = List.of(typeSecret("s1", "auth.phone"));
        assertTrue(new InputGrammarValidator().validate(steps, List.of(), Set.of("auth.phone")).ok());
    }

    @Test
    public void unknownOperationRejectedByCodec() {
        try {
            MobileOperation.parse("EXPLODE");
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().toLowerCase().contains("unsupported")
                    || e.getClass().getSimpleName().contains("Ir"));
        }
    }

    @Test
    public void noInferredAssertions() {
        // MobileStep constructor itself rejects ASSERT without assertType
        try {
            new MobileStep("a1", "s", MobileOperation.ASSERT,
                    new MobileContext("NATIVE", "p", null, null),
                    new MobileTarget("text", "x", "USER", 1),
                    null, null, null, false, new MobileStepEvidence(null, List.of()),
                    null, null, null, null, null, null);
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().toLowerCase().contains("assert"));
        }
    }

    @Test
    public void caseSettingsRevisionBinding() {
        MobileCaseSettings s = new MobileCaseSettings(
                "lib1", "tc1", "mcase1", "PROFILE", "auth1",
                List.of("setup1"), "", "NATIVE", 30_000, true);
        assertEquals(s.caseSettingsRevisionId(), "mcase1");
        assertTrue(s.failFast());
    }
}
