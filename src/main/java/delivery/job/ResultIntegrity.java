package delivery.job;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pass-rate and proof-source labels that do not treat simulated or unchecked
 * work as proven success.
 */
public final class ResultIntegrity {
    private ResultIntegrity() {
    }

    public record JobSlice(int passed, int todo, boolean simulated, boolean unchecked) {
        public static JobSlice proven(int passed, int todo) {
            return new JobSlice(passed, todo, false, false);
        }

        public static JobSlice simulated(int passed, int todo) {
            return new JobSlice(passed, todo, true, false);
        }

        public static JobSlice unchecked(int todo) {
            return new JobSlice(0, todo, false, true);
        }
    }

    public record Summary(
            int provenPassed,
            int blocked,
            int unchecked,
            int simulated,
            int passRate,
            String denominatorNote,
            boolean hasProvenSample
    ) {
        public Map<String, Object> asMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("provenPassed", provenPassed);
            out.put("blocked", blocked);
            out.put("unchecked", unchecked);
            out.put("simulated", simulated);
            out.put("passRate", passRate);
            out.put("denominatorNote", denominatorNote);
            out.put("hasProvenSample", hasProvenSample);
            return out;
        }
    }

    public static Summary summarize(JobSlice... slices) {
        int provenPassed = 0;
        int blocked = 0;
        int unchecked = 0;
        int simulated = 0;
        if (slices != null) {
            for (JobSlice slice : slices) {
                if (slice == null) {
                    continue;
                }
                if (slice.simulated()) {
                    simulated += Math.max(0, slice.passed()) + Math.max(0, slice.todo());
                    continue;
                }
                if (slice.unchecked()) {
                    unchecked += Math.max(0, slice.todo());
                    continue;
                }
                provenPassed += Math.max(0, slice.passed());
                blocked += Math.max(0, slice.todo());
            }
        }
        int judged = provenPassed + blocked + unchecked;
        int rate = judged > 0 ? Math.round(100f * provenPassed / judged) : 0;
        boolean hasSample = judged > 0;
        String note = hasSample
                ? "Pass rate is " + provenPassed + "/" + judged
                + " proven cases (blocked and unchecked stay in the denominator). "
                + simulated + " simulated dry-run case(s) are excluded."
                : "No proven cases yet";
        return new Summary(provenPassed, blocked, unchecked, simulated, rate, note, hasSample);
    }

    public static String proofSource(String proofKind, boolean frameworkReplay) {
        if ("SIMULATED".equals(proofKind)) {
            return "SIMULATED";
        }
        if (frameworkReplay) {
            return "FRAMEWORK_REPLAY";
        }
        if ("FRESH".equals(proofKind) || "REUSED".equals(proofKind) || "BLOCKED".equals(proofKind)) {
            return "BROWSER_PROOF";
        }
        return "UNCHECKED";
    }

    public static boolean simulatedMessage(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("dry-run");
    }
}
