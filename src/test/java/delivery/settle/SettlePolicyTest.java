package delivery.settle;

import delivery.settle.SettlePolicy.*;
import delivery.settle.SideEffectClassifier.Target;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.testng.Assert.*;

public class SettlePolicyTest {

    static String res(String p) throws Exception {
        try (var in = SettlePolicyTest.class.getResourceAsStream(p)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test public void goldenCanonicalForm() throws Exception {
        assertEquals(SettlePolicy.builtin().canonical(), res("/keel/settle-policy.golden.txt").replace("\r\n", "\n")); // r3: CRLF checkout
    }

    @Test public void contractInvariants() throws Exception {
        JSONObject o = new JSONObject(res("/keel/settle-policy.json"));
        assertEquals(o.getString("contract"), "keel.settle-policy");
        assertEquals(o.get("version"), 1);
        JSONObject acts = o.getJSONObject("actions");
        for (String k : new String[]{"tap", "longPress", "back", "type", "typeSecret", "scroll", "click", "select", "drag"})
            assertTrue(acts.has(k), k);
        for (String k : acts.keySet()) assertTrue(acts.getJSONObject(k).getString("sideEffects").matches("none|mutating|unknown"), k);
        SettlePolicy p = SettlePolicy.builtin();
        assertEquals(p.rule(Action.TYPE_SECRET).effectWaitMs(), 0);
        assertEquals(p.rule(Action.TYPE_SECRET).effect(), Effect.NONE);
        assertEquals(p.rule(Action.SCROLL).effectWaitMs(), 500);
        assertEquals(p.rule(Action.SUBMIT).sideEffects(), SideEffects.MUTATING);
        assertEquals(p.rule(Action.HOVER).sideEffects(), SideEffects.NONE);
        assertEquals(p.rule(Action.CLICK).sideEffects(), SideEffects.UNKNOWN);
    }

    static void bad(String good, Consumer<JSONObject> mut, String msgPart) {
        JSONObject o = new JSONObject(good); mut.accept(o);
        try { SettlePolicy.parse(o.toString()); fail("accepted: " + msgPart); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains(msgPart), e.getMessage()); }
    }

    static JSONObject act(JSONObject o, String k) { return o.getJSONObject("actions").getJSONObject(k); }

    @Test public void strictLoaderRejects() throws Exception {
        String g = res("/keel/settle-policy.json");
        SettlePolicy.parse(g);
        bad(g, o -> o.put("contract", "other"), "contract");
        bad(g, o -> o.put("version", 2), "version");
        bad(g, o -> act(o, "click").put("extra", 1), "not a known field");
        bad(g, o -> act(o, "click").remove("sideEffects"), "required");
        bad(g, o -> act(o, "clear").put("effectWaitMs", 0), "effectWaitMs");
        bad(g, o -> act(o, "click").put("sideEffects", "maybe"), "sideEffects");
        bad(g, o -> o.getJSONObject("actions").remove("assert"), "missing");
        bad(g, o -> o.getJSONObject("actions").remove("tap"), "missing");
        bad(g, o -> o.getJSONObject("actions").put("swipe", act(o, "tap")), "swipe");
    }

    @Test public void sideEffectsClassification() throws Exception {
        SettlePolicy p = SettlePolicy.builtin();
        SideEffects N = SideEffects.NONE, M = SideEffects.MUTATING, U = SideEffects.UNKNOWN;
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("button", "button", null, "Save changes")), M);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("button", null, null, "Delete")), M);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, new Target("a", null, null, "Abmelden", "/x", false, false, null)), M);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, new Target("a", null, null, "Bye", "/auth/logout", false, false, null)), M);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, new Target("button", null, null, "Go", null, false, true, "post")), M);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, new Target("button", null, "submit", "Go", null, false, true, "get")), N);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("div", "tab", null, "Accounts")), N);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, new Target("button", null, "button", "Actions", null, true, false, null)), N);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, new Target("a", null, null, "Statements", "/statements", false, false, null)), N);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("th", "columnheader", null, "Amount")), N);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("button", null, null, "Sort by date")), N);
        assertEquals(SideEffectClassifier.classify(p, Action.TYPE, Target.of("input", "searchbox", "search", "Search")), N);
        assertEquals(SideEffectClassifier.classify(p, Action.TYPE, Target.of("input", "textbox", "text", "IBAN")), U);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("div", null, null, "Continue")), U);
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, null), U);
        assertEquals(SideEffectClassifier.classify(p, Action.SCROLL, null), N);
        assertEquals(SideEffectClassifier.classify(p, Action.SUBMIT, Target.of("form", null, null, "Search")), M);
        assertEquals(SideEffectClassifier.classify(p, Action.ASSERT, Target.of("button", null, null, "Delete")), N);
        // mutating word beats read-only signals
        assertEquals(SideEffectClassifier.classify(p, Action.CLICK, Target.of("div", "menuitem", null, "Delete account")), M);
    }

    @Test public void irFieldRules() {
        JSONObject s = new JSONObject().put("action", "click");
        SideEffectClassifier.emit(s, SideEffects.UNKNOWN); assertFalse(s.has("sideEffects"));
        SideEffectClassifier.emit(s, SideEffects.NONE); assertEquals(s.get("sideEffects"), "none");
        assertEquals(SideEffectClassifier.read(new JSONObject().put("action", "click")), SideEffects.UNKNOWN);
        assertEquals(SideEffectClassifier.read(new JSONObject().put("action", "click").put("sideEffects", JSONObject.NULL)), SideEffects.UNKNOWN);
        assertEquals(SideEffectClassifier.read(new JSONObject().put("action", "assert").put("sideEffects", "bogus")), SideEffects.NONE);
        assertThrows(IllegalArgumentException.class, () -> SideEffectClassifier.read(new JSONObject().put("action", "click").put("sideEffects", "maybe")));
        assertThrows(IllegalArgumentException.class, () -> SideEffectClassifier.read(new JSONObject().put("action", "click").put("sideEffects", 1)));
        assertFalse(SideEffects.UNKNOWN.retrySafe()); assertFalse(SideEffects.MUTATING.retrySafe()); assertTrue(SideEffects.NONE.retrySafe());
        JSONObject a = new JSONObject().put("action", "assert");
        SideEffectClassifier.emit(a, SideEffects.NONE); assertFalse(a.has("sideEffects"));
    }
}
