package delivery.portal.service;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcIdentity;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Portal read-model for honest retry metrics (engine writes {@code <runRoot>/retry/<tc>.json}).
 * Self-contained: only reads the sidecar JSON, so the UI works before and after the engine patch.
 * Runs without sidecars show LEGACY (pass) / FAILED and first-pass "n/a".
 */
public final class RetryMetricsView {

    /** Verdict key → badge label, in display order. Also the legend model attribute. */
    public static final Map<String, String> LABELS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("PASSED", "Passed");
        m.put("FLAKY", "Flaky");
        m.put("HEALED", "Healed");
        m.put("FAILED", "Failed");
        m.put("INFRA_ERROR", "Infra");
        m.put("CONFIG_ERROR", "Config");
        m.put("REUSED", "Reused");
        m.put("LEGACY_PASSED", "Legacy");
        LABELS = java.util.Collections.unmodifiableMap(m);
    }

    private RetryMetricsView() {
    }

    /** Legend rows for Thymeleaf: [{key, label, badge}] in display order. */
    public static List<Map<String, String>> legend() {
        List<Map<String, String>> out = new java.util.ArrayList<>();
        LABELS.forEach((k, v) -> out.add(Map.of("key", k, "label", v, "badge", badgeKey(k))));
        return out;
    }

    public static String badgeKey(String verdict) {
        return switch (verdict) {
            case "INFRA_ERROR" -> "infra";
            case "CONFIG_ERROR" -> "config";
            case "LEGACY_PASSED" -> "legacy";
            default -> verdict.toLowerCase(Locale.ROOT);
        };
    }

    /** Per-TC fields merged into the /tcs row: verdict, verdictKey, verdictLabel, reusedFromVerdict, reusedFlaky. */
    public static Map<String, Object> forTc(Path runRoot, TcDraft d) {
        JSONObject rec = read(runRoot, d.tcId());
        String verdict;
        if (rec != null && LABELS.containsKey(rec.optString("verdict"))) {
            verdict = rec.getString("verdict");
        } else if (d.status() == TcDraftStatus.PASSED) {
            verdict = "LEGACY_PASSED";
        } else if (d.status() == TcDraftStatus.REUSED) {
            verdict = "REUSED";
        } else {
            verdict = "FAILED";
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("verdict", verdict);
        m.put("verdictKey", badgeKey(verdict));
        m.put("verdictLabel", LABELS.get(verdict));
        String from = rec == null ? "" : rec.optString("reusedFromVerdict", "");
        m.put("reusedFromVerdict", from);
        m.put("reusedFlaky", rec != null && (rec.optBoolean("reusedFlaky", false) || "FLAKY".equals(from)));
        m.put("testRetries", rec == null ? 0 : rec.optInt("testRetries", 0));
        m.put("infraRetries", rec == null ? 0 : rec.optInt("infraRetries", 0));
        return m;
    }

    /** Run tiles: firstPassRate/finalPassRate (0..1 or null) + labels + counts. */
    public static Map<String, Object> summary(Path runRoot, List<TcDraft> drafts) {
        Map<String, Integer> n = new LinkedHashMap<>();
        LABELS.keySet().forEach(k -> n.put(k, 0));
        int reusedFlaky = 0;
        for (TcDraft d : drafts) {
            Map<String, Object> v = forTc(runRoot, d);
            n.merge((String) v.get("verdict"), 1, Integer::sum);
            if (Boolean.TRUE.equals(v.get("reusedFlaky"))) {
                reusedFlaky++;
            }
        }
        int p = n.get("PASSED"), f = n.get("FLAKY"), h = n.get("HEALED"), x = n.get("FAILED");
        int l = n.get("LEGACY_PASSED");
        int firstDen = p + f + h + x;
        int finalDen = firstDen + l;
        Double first = firstDen == 0 ? null : (double) p / firstDen;
        Double fin = finalDen == 0 ? null : (double) (p + f + h + l) / finalDen;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firstPassRate", first);
        m.put("finalPassRate", fin);
        m.put("firstPassLabel", pct(first));
        m.put("finalPassLabel", pct(fin));
        m.put("passed", p);
        m.put("flaky", f);
        m.put("healed", h);
        m.put("failed", x);
        m.put("infraError", n.get("INFRA_ERROR"));
        m.put("configError", n.get("CONFIG_ERROR"));
        m.put("reused", n.get("REUSED"));
        m.put("reusedFlaky", reusedFlaky);
        m.put("legacyPassed", l);
        m.put("legacy", l > 0 && firstDen == 0);
        return m;
    }

    static String pct(Double r) {
        return r == null ? "n/a" : Math.round(r * 1000) / 10.0 + "%";
    }

    private static JSONObject read(Path runRoot, String tcId) {
        if (runRoot == null || tcId == null) {
            return null;
        }
        try {
            Path f = runRoot.resolve("retry").resolve(TcIdentity.storageKey(tcId) + ".json");
            return Files.isRegularFile(f) ? new JSONObject(Files.readString(f, StandardCharsets.UTF_8)) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
