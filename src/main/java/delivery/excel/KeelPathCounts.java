package delivery.excel;

import delivery.portal.model.KeelPath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts workbook rows by {@code KeelPath}. Blank rows count as {@code BLANK}
 * (not EXECUTE — see {@link GeneratedTcCsvParser#countByKeelPath} for legacy mapping).
 * Non-blank values are normalized via {@link KeelPath#parse}; unknown tokens are skipped
 * and excluded from {@link #toMap()} and runnable totals.
 */
public final class KeelPathCounts {

    private static final List<String> KEYS = List.of(
            "AUTOMATE", "EXECUTE", "VISION_ONLY", "MANUAL", "BLANK");

    private final Map<String, Integer> counts;
    private final int tcCount;

    private KeelPathCounts(Map<String, Integer> counts, int tcCount) {
        this.counts = counts;
        this.tcCount = tcCount;
    }

    public static KeelPathCounts from(List<ManualTestCase> cases) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : KEYS) {
            counts.put(key, 0);
        }
        int total = cases == null ? 0 : cases.size();
        if (cases != null) {
            for (ManualTestCase tc : cases) {
                String key = resolveKey(tc == null ? null : tc.keelPath());
                if (key != null) {
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return new KeelPathCounts(counts, total);
    }

    /**
     * Blank stays {@code BLANK} (do not call {@link KeelPath#parse} on blank — it maps blank→EXECUTE).
     * Known aliases (AUTO, RUN, VISION, …) normalize to canonical enum names.
     * Unknown values return {@code null} and are omitted from counts.
     */
    private static String resolveKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "BLANK";
        }
        try {
            return KeelPath.parse(raw).name();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int get(String key) {
        return counts.getOrDefault(key, 0);
    }

    public Map<String, Integer> toMap() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : KEYS) {
            out.put(key, counts.getOrDefault(key, 0));
        }
        return out;
    }

    /** Rows eligible for Automate: AUTOMATE plus blank (legacy sheets). */
    public int automateRunnable() {
        return get("AUTOMATE") + get("BLANK");
    }

    /**
     * Rows eligible for Execute: AUTOMATE, EXECUTE, VISION_ONLY, plus blank.
     * Only MANUAL is excluded (human review).
     */
    public int executeRunnable() {
        return get("AUTOMATE") + get("EXECUTE") + get("VISION_ONLY") + get("BLANK");
    }

    /** Total workbook rows (including unknown KeelPath values). */
    public int tcCount() {
        return tcCount;
    }
}
