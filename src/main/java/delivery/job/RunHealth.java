package delivery.job;

import org.json.JSONObject;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Run-level honest metrics.
 * <ul>
 *   <li>firstPassRate = PASSED / (PASSED + FLAKY + HEALED + FAILED) — legacy rows excluded (first-pass unknown)</li>
 *   <li>finalPassRate = (PASSED + FLAKY + HEALED + LEGACY_PASSED) / (PASSED + FLAKY + HEALED + FAILED + LEGACY_PASSED)</li>
 *   <li>INFRA_ERROR, CONFIG_ERROR and REUSED are excluded from both denominators and reported as counts.</li>
 * </ul>
 * Rates are null when the denominator is 0 (never report 0 % or 100 % for "nothing ran").
 */
public record RunHealth(int passed, int flaky, int healed, int failed, int infraError, int configError,
                        int reused, int reusedFlaky, int legacyPassed,
                        Double firstPassRate, Double finalPassRate) {

    public static RunHealth of(Collection<RetryVerdict> verdicts) {
        return of(verdicts, 0);
    }

    public static RunHealth of(Collection<RetryVerdict> verdicts, int reusedFlaky) {
        Map<RetryVerdict, Integer> n = new EnumMap<>(RetryVerdict.class);
        for (RetryVerdict v : RetryVerdict.values()) {
            n.put(v, 0);
        }
        if (verdicts != null) {
            for (RetryVerdict v : verdicts) {
                if (v != null) {
                    n.merge(v, 1, Integer::sum);
                }
            }
        }
        int p = n.get(RetryVerdict.PASSED), f = n.get(RetryVerdict.FLAKY), h = n.get(RetryVerdict.HEALED);
        int x = n.get(RetryVerdict.FAILED), l = n.get(RetryVerdict.LEGACY_PASSED);
        int firstDen = p + f + h + x;
        int finalDen = firstDen + l;
        return new RunHealth(p, f, h, x, n.get(RetryVerdict.INFRA_ERROR), n.get(RetryVerdict.CONFIG_ERROR),
                n.get(RetryVerdict.REUSED), Math.max(0, reusedFlaky), l,
                firstDen == 0 ? null : (double) p / firstDen,
                finalDen == 0 ? null : (double) (p + f + h + l) / finalDen);
    }

    public String summary() {
        return "first-pass " + pct(firstPassRate) + ", final-pass " + pct(finalPassRate)
                + ", flaky " + flaky + ", healed " + healed + ", infra errors " + infraError
                + ", config errors " + configError;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("schema", "keel.retry-metrics/v2");
        o.put("passed", passed).put("flaky", flaky).put("healed", healed).put("failed", failed)
                .put("infraError", infraError).put("configError", configError)
                .put("reused", reused).put("reusedFlaky", reusedFlaky).put("legacyPassed", legacyPassed);
        o.put("firstPassRate", firstPassRate == null ? JSONObject.NULL : firstPassRate);
        o.put("finalPassRate", finalPassRate == null ? JSONObject.NULL : finalPassRate);
        return o;
    }

    private static String pct(Double r) {
        return r == null ? "n/a" : Math.round(r * 1000) / 10.0 + "%";
    }
}
