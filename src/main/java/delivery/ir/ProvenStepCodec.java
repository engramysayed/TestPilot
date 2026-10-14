package delivery.ir;

import delivery.codegen.LocatorCandidate;
import delivery.codegen.ProvenStep;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ProvenStepCodec {
    private ProvenStepCodec() {
    }

    /** Where the once-per-load deprecation warning goes (slf4j WARN by default; tests may swap it). */
    static volatile Consumer<String> deprecationSink =
            msg -> org.slf4j.LoggerFactory.getLogger(ProvenStepCodec.class).warn(msg);

    /**
     * One IR load (e.g. one TcDraft file with its provenSteps + loginSteps). Collects deprecations so
     * they are logged once per load, not once per step. Call {@link #finish()} when the load ends.
     */
    public static final class Load {
        private final String source;
        private int absentContractV2Steps;
        private boolean finished;

        public Load(String source) {
            this.source = source == null || source.isBlank() ? "IR" : source;
        }

        public int absentContractV2Steps() {
            return absentContractV2Steps;
        }

        public void finish() {
            if (finished) {
                return;
            }
            finished = true;
            if (absentContractV2Steps > 0) {
                deprecationSink.accept("DEPRECATED: " + source + " has " + absentContractV2Steps
                        + " step(s) using Keel contract-2 locators (text=/provider=/point=) without a \"contract\" field; "
                        + "accepted for now, but writers must emit \"contract\": 2 (absent will be rejected in a future contract).");
            }
        }
    }

    public static JSONObject toJson(ProvenStep s) {
        JSONObject o = new JSONObject();
        o.put("tcId", nullToEmpty(s.tcId()));
        o.put("pageName", nullToEmpty(s.pageName()));
        o.put("actionType", nullToEmpty(s.actionType()));
        o.put("action", nullToEmpty(s.action()));
        o.put("locatorStrategy", nullToEmpty(s.locatorStrategy()));
        o.put("locatorValue", nullToEmpty(s.locatorValue()));
        o.put("value", nullToEmpty(s.value()));
        o.put("assertionType", nullToEmpty(s.assertionType()));
        o.put("assertionExpected", nullToEmpty(s.assertionExpected()));
        o.put("validated", s.validated());
        o.put("rationale", nullToEmpty(s.rationale()));
        o.put("screenshotRelPath", nullToEmpty(s.screenshotRelPath()));
        if (!s.locatorCandidates().isEmpty()) {
            JSONArray cands = new JSONArray();
            for (LocatorCandidate c : s.locatorCandidates()) {
                JSONObject j = new JSONObject();
                j.put("strategy", c.strategy());
                j.put("value", c.value());
                if (!Double.isNaN(c.score())) {
                    j.put("score", c.score());
                }
                cands.put(j);
            }
            o.put("locatorCandidates", cands);
        }
        if (s.isDrag() || !s.dropCandidates().isEmpty()) {
            o.put("actionHint", "drag");
            if (!s.dropCandidates().isEmpty()) {
                LocatorCandidate main = s.dropCandidates().get(0);
                o.put("dropTarget", new JSONObject().put("strategy", main.strategy()).put("value", main.value()).put("score", main.score()));
                JSONArray drops = new JSONArray();
                for (LocatorCandidate c : s.dropCandidates()) {
                    drops.put(new JSONObject().put("strategy", c.strategy()).put("value", c.value()).put("score", c.score()));
                }
                o.put("dropCandidates", drops);
            }
        }
        if (!ProvenStep.SIDE_EFFECTS_UNKNOWN.equals(s.sideEffects())) {
            o.put("sideEffects", s.sideEffects()); // absent == "unknown": existing IR stays byte-identical
        }
        int contract = LocatorCandidate.requiredContract(s);
        if (contract > 1) {
            o.put("contract", contract); // absent == 1: v1 IR stays byte-identical
        }
        if (s.secret()) {
            o.put("secret", true); // absent == false (r2)
        }
        return o;
    }

    /** Single-step load: its own {@link Load} (deprecation logged at most once for this call). */
    public static ProvenStep fromJson(JSONObject o) {
        Load load = new Load("IR step");
        try {
            return fromJson(o, load);
        } finally {
            load.finish();
        }
    }

    public static ProvenStep fromJson(JSONObject o, Load load) {
        // Refuse IR from a newer locator contract before reading anything else (clear, early error).
        int declared = o.optInt("contract", 1);
        LocatorCandidate.requireSupportedContract(declared);
        String action = o.optString("action", "");
        if ("drag".equalsIgnoreCase(o.optString("actionHint", ""))) {
            action = "drag";
        }
        ProvenStep step = new ProvenStep(
                o.optString("tcId", ""),
                o.optString("pageName", "Page"),
                o.optString("actionType", "elementAction"),
                action,
                o.optString("locatorStrategy", ""),
                o.optString("locatorValue", ""),
                o.optString("value", ""),
                o.optString("assertionType", ""),
                o.optString("assertionExpected", ""),
                o.optBoolean("validated", false),
                o.optString("rationale", ""),
                o.optString("screenshotRelPath", ""),
                candidatesFrom(o.has("locatorCandidates") ? o.optJSONArray("locatorCandidates") : o.optJSONArray("fallbacks")),
                dropFrom(o),
                sideEffectsFrom(o),
                SecretStep.read(o)
        );
        if (o.has("contract") && LocatorCandidate.requiredContract(step) > declared) {
            throw new IllegalArgumentException("IR declares Keel locator contract " + declared
                    + " but uses contract " + LocatorCandidate.requiredContract(step) + " hops (text=/provider=/point=)");
        }
        if (!o.has("contract") && LocatorCandidate.requiredContract(step) > 1 && load != null) {
            load.absentContractV2Steps++;
        }
        return step;
    }

    /** {@code sideEffects}: "none" | "mutating" | "unknown"; absent or null == "unknown". */
    static String sideEffectsFrom(JSONObject o) {
        if (!o.has("sideEffects") || o.isNull("sideEffects")) {
            return ProvenStep.SIDE_EFFECTS_UNKNOWN;
        }
        Object v = o.get("sideEffects");
        if (!(v instanceof String s) || !ProvenStep.SIDE_EFFECTS_VALUES.contains(s)) {
            throw new IllegalArgumentException("IR sideEffects must be one of none|mutating|unknown, got " + v);
        }
        return s;
    }

    /** Drag drop target: {@code dropTarget} (KeelTarget string or {strategy,value}) first, then {@code dropCandidates}. */
    static List<LocatorCandidate> dropFrom(JSONObject o) {
        // Same convention as locatorCandidates: dropCandidates = drop main + fallbacks; dropTarget = main.
        List<LocatorCandidate> out = new ArrayList<>(candidatesFrom(o.optJSONArray("dropCandidates")));
        LocatorCandidate main = null;
        Object dt = o.opt("dropTarget");
        if (dt instanceof String str && !str.isBlank()) {
            main = new LocatorCandidate("keel", str, 1);
        } else if (dt instanceof JSONObject j) {
            main = new LocatorCandidate(j.optString("strategy", "keel"), j.optString("value", ""), j.optDouble("score", 1));
        }
        if (main != null) {
            String v = main.value();
            out.removeIf(c -> c.value().equals(v));
            out.add(0, main);
        }
        return out;
    }

    public static JSONArray toJsonArray(List<ProvenStep> steps) {
        JSONArray arr = new JSONArray();
        if (steps == null) {
            return arr;
        }
        for (ProvenStep s : steps) {
            arr.put(toJson(s));
        }
        return arr;
    }

    /** One array == one IR load (deprecation logged at most once). */
    public static List<ProvenStep> fromJsonArray(JSONArray arr) {
        Load load = new Load("IR");
        try {
            return fromJsonArray(arr, load);
        } finally {
            load.finish();
        }
    }

    public static List<ProvenStep> fromJsonArray(JSONArray arr, Load load) {
        List<ProvenStep> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            out.add(fromJson(arr.getJSONObject(i), load));
        }
        return out;
    }

    static List<LocatorCandidate> candidatesFrom(JSONArray arr) {
        List<LocatorCandidate> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) {
                continue;
            }
            // A3 v1: locatorCandidates = main + up to 2 fallbacks, {strategy:"keel", value, score};
            // "fallbacks[]" is only read when locatorCandidates is absent. route and machine-form extras
            // (frames/shadowPath/css) are ignored: the string form is authoritative.
            out.add(new LocatorCandidate(
                    j.optString("strategy", ""),
                    j.optString("value", ""),
                    j.optDouble("score", 0)));
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
