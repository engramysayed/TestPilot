package delivery.routing;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A11: per-intent model routing from {@code /keel/model-routing.json} (after midscene usage-intent / TIntent).
 * Every model call names its {@link Intent}; the router picks the model, and {@link CallLedger} logs the call
 * with the model's cost tag. Observation budgets ({@link #observationChars}) are derived from the routed
 * model's context size instead of fixed caps (after e2e observation-budget.ts).
 */
public final class ModelRouter {
    public static final String CONTRACT = "keel.model-routing";
    public static final int VERSION = 1;
    public static final int MIN_OBSERVATION_CHARS = 1024;

    public enum Intent {
        GROUNDING("grounding"), ASSERTION("assertion"), BIND("bind"), HEAL("heal"), PLANNING("planning"),
        PRECISION("precision"), HUNT_PLANNING("huntPlanning"), GENERATE("generate"), NAMING("naming");

        public final String key;

        Intent(String k) { key = k; }
    }

    public record ModelSpec(String key, String provider, String model, int contextTokens, double charsPerToken, boolean vision,
                            String costTag, double costPer1kIn, double costPer1kOut) {
    }

    public record Route(Intent intent, ModelSpec model, int reserveTokens, int imageTokens, double observationShare) {
    }

    private final Map<String, ModelSpec> models;
    private final Map<Intent, JSONObject> intents;

    private ModelRouter(Map<String, ModelSpec> models, Map<Intent, JSONObject> intents) {
        this.models = models;
        this.intents = intents;
    }

    private static volatile ModelRouter builtin;

    public static ModelRouter builtin() {
        ModelRouter r = builtin;
        if (r == null) {
            try (InputStream in = ModelRouter.class.getResourceAsStream("/keel/model-routing.json")) {
                if (in == null) throw new IllegalStateException("missing /keel/model-routing.json");
                r = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            builtin = r;
        }
        return r;
    }

    static final Set<String> MODEL_FIELDS = Set.of("provider", "model", "contextTokens", "charsPerToken", "vision", "costTag", "costPer1kIn", "costPer1kOut");

    public static ModelRouter parse(String json) {
        JSONObject o = new JSONObject(json);
        if (!CONTRACT.equals(o.optString("contract")) || o.optInt("version") != VERSION)
            throw new IllegalArgumentException("model routing: contract/version mismatch");
        Map<String, ModelSpec> models = new HashMap<>();
        JSONObject ms = o.getJSONObject("models");
        for (String k : ms.keySet()) {
            JSONObject m = ms.getJSONObject(k);
            for (String f : m.keySet()) if (!MODEL_FIELDS.contains(f)) throw new IllegalArgumentException("model routing: models." + k + "." + f + " unknown");
            ModelSpec spec = new ModelSpec(k, m.getString("provider"), m.getString("model"), m.getInt("contextTokens"),
                    m.getDouble("charsPerToken"), m.getBoolean("vision"), m.getString("costTag"), m.getDouble("costPer1kIn"), m.getDouble("costPer1kOut"));
            if (spec.contextTokens() <= 0 || spec.charsPerToken() <= 0) throw new IllegalArgumentException("model routing: " + k + " sizes must be > 0");
            models.put(k, spec);
        }
        Map<Intent, JSONObject> intents = new EnumMap<>(Intent.class);
        JSONObject is = o.getJSONObject("intents");
        for (Intent i : Intent.values()) {
            if (!is.has(i.key)) throw new IllegalArgumentException("model routing: intent '" + i.key + "' missing");
            JSONObject j = is.getJSONObject(i.key);
            if (!models.containsKey(j.getString("model"))) throw new IllegalArgumentException("model routing: intent " + i.key + " -> unknown model " + j.getString("model"));
            intents.put(i, j);
        }
        for (String k : is.keySet()) {
            boolean known = false;
            for (Intent i : Intent.values()) known |= i.key.equals(k);
            if (!known) throw new IllegalArgumentException("model routing: unknown intent '" + k + "'");
        }
        ModelRouter r = new ModelRouter(models, intents);
        for (Intent i : new Intent[]{Intent.GROUNDING, Intent.ASSERTION}) {
            if (!r.route(i, k -> null).model().vision()) throw new IllegalArgumentException("model routing: " + i.key + " needs a vision model");
        }
        return r;
    }

    public Route route(Intent intent) {
        return route(intent, System::getProperty);
    }

    Route route(Intent intent, java.util.function.Function<String, String> props) {
        JSONObject j = intents.get(intent);
        String key = j.getString("model");
        String ov = props.apply("keel.route." + intent.key);
        if (ov != null && !ov.isBlank()) {
            key = ov.trim();
            if (!models.containsKey(key)) throw new IllegalArgumentException("keel.route." + intent.key + "=" + key + ": unknown model key");
        }
        return new Route(intent, models.get(key), j.getInt("reserveTokens"), j.getInt("imageTokens"), j.getDouble("observationShare"));
    }

    /** Char budget for the observation part of a prompt (PageModel render, slim HTML) on the routed model. */
    public static int observationChars(Route r) {
        double tokens = (r.model().contextTokens() - r.reserveTokens() - r.imageTokens()) * r.observationShare();
        return (int) Math.max(MIN_OBSERVATION_CHARS, Math.floor(tokens * r.model().charsPerToken()));
    }

    public ModelSpec model(String key) {
        ModelSpec m = models.get(key);
        if (m == null) throw new IllegalArgumentException("unknown model key " + key);
        return m;
    }

    static String lc(String s) { return s.toLowerCase(Locale.ROOT); }
}
