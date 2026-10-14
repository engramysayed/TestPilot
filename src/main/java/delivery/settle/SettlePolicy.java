package delivery.settle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A8: per-action settle policy loaded from {@code /keel/settle-policy.json} (the single source of truth;
 * Keel Codegen copies the file verbatim into the generated ZIP). Strict: unknown actions/fields, a
 * missing action kind, or a contract/version mismatch are load errors, so prove and replay can never
 * silently disagree.
 */
public final class SettlePolicy {
    public static final String CONTRACT = "keel.settle-policy";
    public static final int VERSION = 1;
    public static final String RESOURCE = "/keel/settle-policy.json";

    public enum Action {
        CLICK("click"), DOUBLE_CLICK("doubleClick"), RIGHT_CLICK("rightClick"), HOVER("hover"), TYPE("type"),
        TYPE_SECRET("typeSecret"), CLEAR("clear"), PRESS("press"), SELECT("select"), CHECK("check"), SUBMIT("submit"),
        NAVIGATE("navigate"), BACK("back"), RELOAD("reload"), SCROLL("scroll"), SCROLL_INTO_VIEW("scrollIntoView"),
        DRAG("drag"), UPLOAD("upload"), SWITCH_FRAME("switchFrame"), SWITCH_WINDOW("switchWindow"), WAIT("wait"),
        ASSERT("assert"),
        /** Mobile kinds (Android plan), same table. */
        TAP("tap"), LONG_PRESS("longPress");

        public final String key;

        Action(String key) { this.key = key; }

        public static Action of(String key) {
            for (Action a : values()) if (a.key.equals(key)) return a;
            throw new IllegalArgumentException("unknown settle action '" + key + "'");
        }
    }

    public enum Network { IDLE, NONE }

    /** URL expectation: any, must stay the same, or must change (navigate/back). */
    public enum Url { ANY, SAME, CHANGED }

    /** A7 effect expectation: NO_EFFECT is a failure (required), recorded (optional), or not checked (none). */
    public enum Effect { REQUIRED, OPTIONAL, NONE }

    /** Per-step IR {@code sideEffects} (Codegen r1/05). Absent/null == UNKNOWN, treated as MUTATING. */
    public enum SideEffects {
        NONE("none"), MUTATING("mutating"), UNKNOWN("unknown");
        public final String wire;
        SideEffects(String w) { wire = w; }
        public static SideEffects of(Object v) {
            if (v == null || v == org.json.JSONObject.NULL) return UNKNOWN;
            if (v instanceof String s) for (SideEffects e : values()) if (e.wire.equals(s)) return e;
            throw new IllegalArgumentException("sideEffects must be one of none|mutating|unknown, got " + v);
        }
        /** Only a step proven read-only may be re-run (retry, loop repeat). */
        public boolean retrySafe() { return this == NONE; }
    }

    public record Rule(Action action, long timeoutMs, Network network, long networkIdleMs, long domStableMs,
                       boolean animations, boolean spinners, Url url, Effect effect, long effectWaitMs, SideEffects sideEffects) {
    }

    static final Set<String> RULE_FIELDS = Set.of("timeoutMs", "network", "networkIdleMs", "domStableMs", "animations",
            "spinners", "url", "effect", "effectWaitMs", "sideEffects");

    private final long pollMs;
    private final List<String> spinnerSelectors;
    private final Map<Action, Rule> rules;

    private SettlePolicy(long pollMs, List<String> spinnerSelectors, Map<Action, Rule> rules) {
        this.pollMs = pollMs;
        this.spinnerSelectors = spinnerSelectors;
        this.rules = rules;
    }

    private static volatile SettlePolicy builtin;

    public static SettlePolicy builtin() {
        SettlePolicy p = builtin;
        if (p == null) {
            try (InputStream in = SettlePolicy.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("missing " + RESOURCE);
                p = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            builtin = p;
        }
        return p;
    }

    public static SettlePolicy parse(String json) {
        JSONObject o = new JSONObject(json);
        if (!CONTRACT.equals(o.optString("contract")))
            throw new IllegalArgumentException("settle policy: contract must be '" + CONTRACT + "'");
        int v = o.optInt("version", -1);
        if (v != VERSION) throw new IllegalArgumentException("settle policy: version " + v + " not supported (want " + VERSION + ")");
        JSONObject d = o.getJSONObject("defaults");
        long poll = positive(d.getLong("pollMs"), "defaults.pollMs");
        List<String> spinners = new ArrayList<>();
        JSONArray sp = d.getJSONArray("spinnerSelectors");
        for (int i = 0; i < sp.length(); i++) spinners.add(sp.getString(i));
        JSONObject acts = o.getJSONObject("actions");
        Map<Action, Rule> rules = new EnumMap<>(Action.class);
        for (String k : acts.keySet()) {
            Action a = Action.of(k);
            JSONObject r = acts.getJSONObject(k);
            for (String f : r.keySet())
                if (!RULE_FIELDS.contains(f)) throw new IllegalArgumentException("settle policy: " + k + "." + f + " is not a known field");
            for (String f : RULE_FIELDS)
                if (!r.has(f)) throw new IllegalArgumentException("settle policy: " + k + "." + f + " is required");
            Rule rule = new Rule(a, nonNeg(r.getLong("timeoutMs"), k + ".timeoutMs"), enumOf(Network.class, r.getString("network"), k),
                    nonNeg(r.getLong("networkIdleMs"), k), nonNeg(r.getLong("domStableMs"), k), r.getBoolean("animations"),
                    r.getBoolean("spinners"), enumOf(Url.class, r.getString("url"), k), enumOf(Effect.class, r.getString("effect"), k),
                    nonNeg(r.getLong("effectWaitMs"), k), SideEffects.of(r.get("sideEffects")));
            if (rule.network() == Network.IDLE && rule.networkIdleMs() == 0)
                throw new IllegalArgumentException("settle policy: " + k + " network=idle needs networkIdleMs > 0");
            if (rule.effect() != Effect.NONE && rule.effectWaitMs() == 0)
                throw new IllegalArgumentException("settle policy: " + k + " effect=" + rule.effect() + " needs effectWaitMs > 0");
            rules.put(a, rule);
        }
        for (Action a : Action.values())
            if (!rules.containsKey(a)) throw new IllegalArgumentException("settle policy: action '" + a.key + "' is missing");
        return new SettlePolicy(poll, Collections.unmodifiableList(spinners), Collections.unmodifiableMap(rules));
    }

    public Rule rule(Action a) {
        return rules.get(a);
    }

    public long pollMs() { return pollMs; }

    public List<String> spinnerSelectors() { return spinnerSelectors; }

    /** Canonical one-line-per-action form (golden test + Codegen parity check). */
    public String canonical() {
        StringBuilder sb = new StringBuilder("contract=" + CONTRACT + " version=" + VERSION + " pollMs=" + pollMs + "\n");
        for (Action a : Action.values()) {
            Rule r = rules.get(a);
            sb.append(a.key).append(" timeout=").append(r.timeoutMs()).append(" net=").append(lc(r.network()))
                    .append('/').append(r.networkIdleMs()).append(" dom=").append(r.domStableMs())
                    .append(" anim=").append(r.animations()).append(" spin=").append(r.spinners())
                    .append(" url=").append(lc(r.url())).append(" effect=").append(lc(r.effect())).append('/').append(r.effectWaitMs()).append(" sideEffects=").append(r.sideEffects().wire)
                    .append('\n');
        }
        return sb.toString();
    }

    private static String lc(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }

    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, String ctx) {
        try {
            return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("settle policy: " + ctx + " bad " + c.getSimpleName() + " '" + v + "'");
        }
    }

    private static long nonNeg(long v, String ctx) {
        if (v < 0) throw new IllegalArgumentException("settle policy: " + ctx + " must be >= 0");
        return v;
    }

    private static long positive(long v, String ctx) {
        if (v <= 0) throw new IllegalArgumentException("settle policy: " + ctx + " must be > 0");
        return v;
    }
}
