package delivery.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Warn-only coverage technique gaps (playbook). Does not fail the quality gate by itself.
 */
public final class CoverageTechniqueCheck {
    public record Warning(String technique, List<String> missingInTcs) {
        public Warning {
            missingInTcs = missingInTcs == null ? List.of() : List.copyOf(missingInTcs);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("technique", technique);
            m.put("missingInTcs", missingInTcs);
            return m;
        }
    }

    private CoverageTechniqueCheck() {
    }

    public static List<Warning> warn(String coverageNotes, List<ManualTestCase> cases) {
        String notes = coverageNotes == null ? "" : coverageNotes.toLowerCase(Locale.ROOT);
        String caseBlob = caseBlob(cases);
        List<Warning> out = new ArrayList<>();

        check(out, "equivalence partitioning", notes, caseBlob, cases,
                List.of("equivalence", "partition", "valid class", "invalid class"),
                List.of("valid", "invalid", "equivalence"));
        check(out, "boundary value analysis", notes, caseBlob, cases,
                List.of("boundary", "min", "max", "just inside", "just outside"),
                List.of("boundary", "min", "max", "limit"));
        check(out, "negative paths", notes, caseBlob, cases,
                List.of("negative", "error message", "validation", "unauthorized"),
                List.of("negative", "invalid", "error", "empty "));
        check(out, "state transitions", notes, caseBlob, cases,
                List.of("state transition", "empty →", "submitted", "success/failure"),
                List.of("submit", "empty", "filled", "success", "fail"));
        check(out, "role/permission variants", notes, caseBlob, cases,
                List.of("role", "permission", "unauthorized", "admin", "member"),
                List.of("role", "permission", "admin", "unauthorized"));
        check(out, "smoke vs regression tags", notes, caseBlob, cases,
                List.of("smoke", "regression"),
                List.of("smoke", "regression"));
        check(out, "visual/regression", notes, caseBlob, cases,
                List.of("visual", "vision_only", "screenshot", "design"),
                List.of("visual", "screenshot", "looks like"));

        if ((coverageNotes == null || coverageNotes.isBlank()) && cases != null && !cases.isEmpty()) {
            out.add(0, new Warning("coverageNotes", List.of(
                    "(batch) — model returned empty coverageNotes; techniques applied are unknown")));
        }
        return List.copyOf(out);
    }

    public static List<Map<String, Object>> toMaps(List<Warning> warnings) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Warning w : warnings) {
            out.add(w.toMap());
        }
        return out;
    }

    private static void check(
            List<Warning> out,
            String technique,
            String notes,
            String caseBlob,
            List<ManualTestCase> cases,
            List<String> noteHints,
            List<String> caseHints
    ) {
        boolean inNotes = containsAny(notes, noteHints);
        boolean inCases = containsAny(caseBlob, caseHints);
        if (inNotes || inCases) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (cases != null) {
            for (ManualTestCase tc : cases) {
                if (tc != null && tc.tcId() != null && !tc.tcId().isBlank()) {
                    missing.add(tc.tcId());
                }
            }
        }
        if (missing.isEmpty()) {
            missing = List.of("(batch)");
        }
        // Cap list size for UI
        if (missing.size() > 12) {
            missing = new ArrayList<>(missing.subList(0, 12));
            missing.add("…");
        }
        out.add(new Warning(technique, missing));
    }

    private static boolean containsAny(String blob, List<String> needles) {
        if (blob == null || blob.isBlank()) {
            return false;
        }
        for (String n : needles) {
            if (blob.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String caseBlob(List<ManualTestCase> cases) {
        if (cases == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ManualTestCase tc : cases) {
            if (tc == null) continue;
            sb.append(' ').append(nullToEmpty(tc.title()).toLowerCase(Locale.ROOT));
            sb.append(' ').append(nullToEmpty(tc.tags()).toLowerCase(Locale.ROOT));
            sb.append(' ').append(nullToEmpty(tc.steps()).toLowerCase(Locale.ROOT));
            sb.append(' ').append(nullToEmpty(tc.keelPath()).toLowerCase(Locale.ROOT));
            sb.append(' ').append(nullToEmpty(tc.visualAssertion()).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
