package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.settle.SettlePolicy.Action;
import delivery.settle.SideEffectClassifier.Target;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class SideEffectWiringTest {
    @AfterMethod(alwaysRun = true) public void clear() { System.clearProperty("delivery.sideeffects.classify"); }

    static Target t(String tag, String type, String name, boolean inForm, String method) {
        return new Target(tag, null, type, name, null, false, inForm, method);
    }

    @Test public void writesOnlyKnownValuesOverUnknown() {
        assertEquals(SideEffectWiring.resolve("unknown", Action.CLICK, t("button", null, "Save", true, "post")), "mutating");
        assertEquals(SideEffectWiring.resolve(null, Action.CLICK, new Target("a", null, null, "Details", "#d", false, false, null)), "none");
        // classifier says UNKNOWN -> the field stays as it was
        assertEquals(SideEffectWiring.resolve("unknown", Action.CLICK, t("button", null, "Frobnicate", false, null)), "unknown");
        assertNull(SideEffectWiring.resolve(null, Action.CLICK, null));
    }

    @Test public void neverOverwritesAKnownValueAndSkipsAssertions() {
        assertEquals(SideEffectWiring.resolve("none", Action.CLICK, t("button", null, "Delete", false, null)), "none");
        assertEquals(SideEffectWiring.resolve("mutating", Action.CLICK, new Target("a", null, null, "Details", "#d", false, false, null)), "mutating");
        assertEquals(SideEffectWiring.resolve("unknown", Action.ASSERT, null), "unknown");
        assertEquals(SideEffectWiring.resolve("unknown", null, null), "unknown");
    }

    @Test public void applyWithoutDriverUsesTheTableOnlyAndCanBeSwitchedOff() {
        ProvenStep nav = new ProvenStep("TC1", "P", "browserAction", "navigate", "", "", "https://x", "", "", true, "r");
        ProvenStep assertion = new ProvenStep("TC1", "P", "", "assert", "", "", "", "visible", "x", true, "r");
        ProvenStep a = SideEffectWiring.apply(null, assertion, null);
        assertSame(a, assertion);
        String navSe = SideEffectWiring.apply(null, nav, null).sideEffects();
        assertTrue(ProvenStep.SIDE_EFFECTS_VALUES.contains(navSe), navSe);
        System.setProperty("delivery.sideeffects.classify", "false");
        assertSame(SideEffectWiring.apply(null, nav, null), nav);
    }
}
