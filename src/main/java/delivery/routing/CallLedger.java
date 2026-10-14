package delivery.routing;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Per-call usage log with intent and cost tag. One structured log line per call
 * ({@code MODEL_CALL {...json...}}); calls inside a {@link #scope()} are also collected for the job cost report.
 * Token counts are estimated from chars (model charsPerToken) unless the provider reports them.
 */
public final class CallLedger {
    private static final Logger LOG = LoggerFactory.getLogger(CallLedger.class);
    private static final ThreadLocal<List<Entry>> SCOPE = new ThreadLocal<>();

    public record Entry(String intent, String modelKey, String model, String provider, String costTag, int inChars, int outChars,
                        long estInTokens, long estOutTokens, double costUnits, long ms, boolean ok) {
        public JSONObject toJson() {
            return new JSONObject().put("intent", intent).put("modelKey", modelKey).put("model", model).put("provider", provider)
                    .put("costTag", costTag).put("inChars", inChars).put("outChars", outChars).put("estInTokens", estInTokens)
                    .put("estOutTokens", estOutTokens).put("costUnits", Math.round(costUnits * 10000) / 10000.0).put("ms", ms).put("ok", ok);
        }
    }

    private CallLedger() {
    }

    public static Entry record(ModelRouter.Route r, int inChars, int outChars, long ms, boolean ok) {
        ModelRouter.ModelSpec m = r.model();
        long ti = Math.round(inChars / m.charsPerToken()), to = Math.round(outChars / m.charsPerToken());
        double cost = ti / 1000.0 * m.costPer1kIn() + to / 1000.0 * m.costPer1kOut();
        Entry e = new Entry(r.intent().key, m.key(), m.model(), m.provider(), m.costTag(), inChars, outChars, ti, to, cost, ms, ok);
        LOG.info("MODEL_CALL {}", e.toJson());
        List<Entry> s = SCOPE.get();
        if (s != null) s.add(e);
        return e;
    }

    /** Times {@code call}, logs it (also on failure) and returns its result. {@code inChars} = prompt size. */
    public static String timed(ModelRouter.Route r, int inChars, Callable<String> call) throws Exception {
        long t0 = System.currentTimeMillis();
        try {
            String out = call.call();
            record(r, inChars, out == null ? 0 : out.length(), System.currentTimeMillis() - t0, true);
            return out;
        } catch (Exception e) {
            record(r, inChars, 0, System.currentTimeMillis() - t0, false);
            throw e;
        }
    }

    /** Collect entries on this thread (one job) until closed. */
    public static Scope scope() {
        List<Entry> prev = SCOPE.get();
        List<Entry> mine = new ArrayList<>();
        SCOPE.set(mine);
        return new Scope(mine, prev);
    }

    public static final class Scope implements AutoCloseable {
        private final List<Entry> entries;
        private final List<Entry> prev;

        Scope(List<Entry> entries, List<Entry> prev) { this.entries = entries; this.prev = prev; }

        public List<Entry> entries() { return Collections.unmodifiableList(entries); }

        /** Totals per intent and per cost tag. */
        public JSONObject summary() {
            JSONObject byIntent = new JSONObject(), byTag = new JSONObject();
            for (Entry e : entries) {
                add(byIntent, e.intent(), e);
                add(byTag, e.costTag(), e);
            }
            return new JSONObject().put("calls", entries.size()).put("byIntent", byIntent).put("byCostTag", byTag);
        }

        private static void add(JSONObject o, String k, Entry e) {
            JSONObject t = o.optJSONObject(k);
            if (t == null) { t = new JSONObject().put("calls", 0).put("costUnits", 0.0).put("inChars", 0); o.put(k, t); }
            t.put("calls", t.getInt("calls") + 1).put("costUnits", Math.round((t.getDouble("costUnits") + e.costUnits()) * 10000) / 10000.0)
                    .put("inChars", t.getInt("inChars") + e.inChars());
        }

        @Override public void close() {
            if (prev == null) SCOPE.remove(); else SCOPE.set(prev);
        }
    }
}
