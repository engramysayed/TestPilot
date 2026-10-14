package delivery.hunt;

import java.util.List;
import java.util.Map;

/**
 * P1 pack SUMMARY quality counts: first-pass (oracle, no dup), flaky (dupCount&gt;1), confirmed (admitted).
 */
public final class HuntPackQuality {
    private HuntPackQuality() {}

    public record Counts(int confirmed, int firstPass, int flaky, int oracle, int planner) {}

    public static Counts of(List<Map<String, Object>> bugs) {
        int confirmed = 0, firstPass = 0, flaky = 0, oracle = 0, planner = 0;
        if (bugs != null) {
            for (Map<String, Object> b : bugs) {
                confirmed++;
                boolean isOracle = Boolean.TRUE.equals(b.get("oracleSource"))
                        || "true".equalsIgnoreCase(String.valueOf(b.get("oracleSource")));
                if (isOracle) oracle++; else planner++;
                int dup = 1;
                Object d = b.get("dupCount");
                if (d instanceof Number n) dup = n.intValue();
                if (dup > 1) flaky++;
                else firstPass++;
            }
        }
        return new Counts(confirmed, firstPass, flaky, oracle, planner);
    }

    public static String summaryLines(Counts c) {
        if (c == null) c = new Counts(0, 0, 0, 0, 0);
        double firstPassRate = c.confirmed() == 0 ? 0.0 : (100.0 * c.firstPass() / c.confirmed());
        return """
                - Confirmed bugs: %d (oracle: %d, planner: %d)
                - First-pass (unique, no dup): %d
                - Flaky (same fingerprint re-filed): %d
                - First-pass rate: %.0f%%
                """.formatted(c.confirmed(), c.oracle(), c.planner(), c.firstPass(), c.flaky(), firstPassRate)
                .stripTrailing();
    }

    /** Frontend triage status: FIRST_PASS | FLAKY | CONFIRMED (dupCount heuristic). */
    public static String qaStatusFor(Map<String, Object> bug) {
        if (bug == null) return "FIRST_PASS";
        Object d = bug.get("dupCount");
        int dup = 1;
        if (d instanceof Number n) dup = n.intValue();
        else if (d != null) {
            try { dup = Integer.parseInt(d.toString().trim()); } catch (Exception ignored) {}
        }
        // AE-D6: oracle alone is FIRST_PASS — CONFIRMED is human triage or repeated fingerprint
        if (dup >= 3) return "FLAKY";
        if (dup >= 2) return "CONFIRMED";
        return "FIRST_PASS";
    }

    private static boolean truthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(o.toString());
    }
}
