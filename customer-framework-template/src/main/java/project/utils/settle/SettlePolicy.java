package project.utils.settle;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
 * A8 per-action settle policy, loaded from {@code /keel/settle-policy.json}. That file, the probe
 * {@code /keel/settle-probe.js} and the golden {@code settle-policy.golden.txt} are byte-for-byte
 * copies of Keel Automation Engine A8 (patches/A8); never edit them here. This class is a port of
 * the engine's {@code delivery.settle.SettlePolicy} (json-simple instead of org.json) with the same
 * strict validation and the same {@link #canonical()} form, pinned by the golden file.
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
        ASSERT("assert"), TAP("tap"), LONG_PRESS("longPress");

        public final String key;

        Action(String key) {
            this.key = key;
        }

        public static Action of(String key) {
            for (Action a : values()) {
                if (a.key.equals(key)) {
                    return a;
                }
            }
            throw new IllegalArgumentException("unknown settle action '" + key + "'");
        }
    }

    public enum Network { IDLE, NONE }

    public enum Url { ANY, SAME, CHANGED }

    public enum Effect { REQUIRED, OPTIONAL, NONE }

    /** Kind default for the IR {@code sideEffects} marker. Absent/null == UNKNOWN (treated as MUTATING). */
    public enum SideEffects {
        NONE("none"), MUTATING("mutating"), UNKNOWN("unknown");
        public final String wire;

        SideEffects(String w) {
            wire = w;
        }

        public static SideEffects of(Object v) {
            if (v == null) {
                return UNKNOWN;
            }
            if (v instanceof String s) {
                for (SideEffects e : values()) {
                    if (e.wire.equals(s)) {
                        return e;
                    }
                }
            }
            throw new IllegalArgumentException("sideEffects must be one of none|mutating|unknown, got " + v);
        }

        /** Only a step proven read-only may be re-run. */
        public boolean retrySafe() {
            return this == NONE;
        }
    }

    public record Rule(Action action, long timeoutMs, Network network, long networkIdleMs, long domStableMs,
                       boolean animations, boolean spinners, Url url, Effect effect, long effectWaitMs,
                       SideEffects sideEffects) {
    }

    static final Set<String> RULE_FIELDS = Set.of("timeoutMs", "network", "networkIdleMs", "domStableMs", "animations",
            "spinners", "url", "effect", "effectWaitMs", "sideEffects");

    private static volatile SettlePolicy builtin;

    private final long pollMs;
    private final List<String> spinnerSelectors;
    private final Map<Action, Rule> rules;

    private SettlePolicy(long pollMs, List<String> spinnerSelectors, Map<Action, Rule> rules) {
        this.pollMs = pollMs;
        this.spinnerSelectors = spinnerSelectors;
        this.rules = rules;
    }

    public static SettlePolicy builtin() {
        SettlePolicy p = builtin;
        if (p == null) {
            try (InputStream in = SettlePolicy.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + RESOURCE);
                }
                p = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            builtin = p;
        }
        return p;
    }

    public static SettlePolicy parse(String json) {
        JSONObject o;
        try {
            o = (JSONObject) new JSONParser().parse(json);
        } catch (ParseException | ClassCastException e) {
            throw new IllegalArgumentException("settle policy: not a JSON object: " + e.getMessage(), e);
        }
        if (!CONTRACT.equals(o.get("contract"))) {
            throw new IllegalArgumentException("settle policy: contract must be '" + CONTRACT + "'");
        }
        long v = o.get("version") instanceof Long l ? l : -1;
        if (v != VERSION) {
            throw new IllegalArgumentException("settle policy: version " + v + " not supported (want " + VERSION + ")");
        }
        JSONObject d = obj(o, "defaults");
        long poll = positive(lng(d, "pollMs", "defaults.pollMs"), "defaults.pollMs");
        List<String> spinners = new ArrayList<>();
        Object sp = d.get("spinnerSelectors");
        if (!(sp instanceof JSONArray arr)) {
            throw new IllegalArgumentException("settle policy: defaults.spinnerSelectors must be an array");
        }
        for (Object s : arr) {
            if (!(s instanceof String str)) {
                throw new IllegalArgumentException("settle policy: spinner selectors must be strings");
            }
            spinners.add(str);
        }
        JSONObject acts = obj(o, "actions");
        Map<Action, Rule> rules = new EnumMap<>(Action.class);
        for (Object key : acts.keySet()) {
            String k = (String) key;
            Action a = Action.of(k);
            JSONObject r = obj(acts, k);
            for (Object f : r.keySet()) {
                if (!RULE_FIELDS.contains(f)) {
                    throw new IllegalArgumentException("settle policy: " + k + "." + f + " is not a known field");
                }
            }
            for (String f : RULE_FIELDS) {
                if (!r.containsKey(f)) {
                    throw new IllegalArgumentException("settle policy: " + k + "." + f + " is required");
                }
            }
            Rule rule = new Rule(a, nonNeg(lng(r, "timeoutMs", k), k + ".timeoutMs"),
                    enumOf(Network.class, str(r, "network", k), k),
                    nonNeg(lng(r, "networkIdleMs", k), k), nonNeg(lng(r, "domStableMs", k), k), bool(r, "animations", k),
                    bool(r, "spinners", k), enumOf(Url.class, str(r, "url", k), k), enumOf(Effect.class, str(r, "effect", k), k),
                    nonNeg(lng(r, "effectWaitMs", k), k), SideEffects.of(r.get("sideEffects")));
            if (rule.network() == Network.IDLE && rule.networkIdleMs() == 0) {
                throw new IllegalArgumentException("settle policy: " + k + " network=idle needs networkIdleMs > 0");
            }
            if (rule.effect() != Effect.NONE && rule.effectWaitMs() == 0) {
                throw new IllegalArgumentException("settle policy: " + k + " effect=" + rule.effect() + " needs effectWaitMs > 0");
            }
            rules.put(a, rule);
        }
        for (Action a : Action.values()) {
            if (!rules.containsKey(a)) {
                throw new IllegalArgumentException("settle policy: action '" + a.key + "' is missing");
            }
        }
        return new SettlePolicy(poll, Collections.unmodifiableList(spinners), Collections.unmodifiableMap(rules));
    }

    public Rule rule(Action a) {
        return rules.get(a);
    }

    /** Rule for an A8 action key as emitted by codegen ("click", "type", "select", "drag", ...). */
    public Rule rule(String actionKey) {
        return rules.get(Action.of(actionKey));
    }

    public long pollMs() {
        return pollMs;
    }

    public List<String> spinnerSelectors() {
        return spinnerSelectors;
    }

    /** Canonical one-line-per-action form; must equal A8's settle-policy.golden.txt. */
    public String canonical() {
        StringBuilder sb = new StringBuilder("contract=" + CONTRACT + " version=" + VERSION + " pollMs=" + pollMs + "\n");
        for (Action a : Action.values()) {
            Rule r = rules.get(a);
            sb.append(a.key).append(" timeout=").append(r.timeoutMs()).append(" net=").append(lc(r.network()))
                    .append('/').append(r.networkIdleMs()).append(" dom=").append(r.domStableMs())
                    .append(" anim=").append(r.animations()).append(" spin=").append(r.spinners())
                    .append(" url=").append(lc(r.url())).append(" effect=").append(lc(r.effect())).append('/').append(r.effectWaitMs())
                    .append(" sideEffects=").append(r.sideEffects().wire)
                    .append('\n');
        }
        return sb.toString();
    }

    private static String lc(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static JSONObject obj(JSONObject o, String k) {
        if (!(o.get(k) instanceof JSONObject j)) {
            throw new IllegalArgumentException("settle policy: '" + k + "' must be an object");
        }
        return j;
    }

    private static long lng(JSONObject o, String k, String ctx) {
        if (!(o.get(k) instanceof Long l)) {
            throw new IllegalArgumentException("settle policy: " + ctx + " '" + k + "' must be an integer");
        }
        return l;
    }

    private static boolean bool(JSONObject o, String k, String ctx) {
        if (!(o.get(k) instanceof Boolean b)) {
            throw new IllegalArgumentException("settle policy: " + ctx + " '" + k + "' must be a boolean");
        }
        return b;
    }

    private static String str(JSONObject o, String k, String ctx) {
        if (!(o.get(k) instanceof String s)) {
            throw new IllegalArgumentException("settle policy: " + ctx + " '" + k + "' must be a string");
        }
        return s;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> c, String v, String ctx) {
        try {
            return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("settle policy: " + ctx + " bad " + c.getSimpleName() + " '" + v + "'");
        }
    }

    private static long nonNeg(long v, String ctx) {
        if (v < 0) {
            throw new IllegalArgumentException("settle policy: " + ctx + " must be >= 0");
        }
        return v;
    }

    private static long positive(long v, String ctx) {
        if (v <= 0) {
            throw new IllegalArgumentException("settle policy: " + ctx + " must be > 0");
        }
        return v;
    }
}
