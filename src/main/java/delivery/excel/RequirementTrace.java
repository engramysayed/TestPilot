package delivery.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traceability from a test case back to the user story / acceptance criterion it covers.
 * <p>
 * Stored as {@code req:<ref>} tokens in the case's Tags (no schema change, survives Excel/CSV round trips,
 * and suites can filter by it: tag {@code req:US-12} = every case for story US-12). Generation prompts ask the
 * model for {@code requirementRef}; {@link GeneratedTcJsonParser} turns it into tags.
 */
public final class RequirementTrace {

    public static final String PREFIX = "req:";

    private RequirementTrace() {
    }

    /** Append {@code req:<ref>} for each comma/semicolon separated ref not already present. */
    public static String withRequirementTags(String tags, String requirementRef) {
        String t = tags == null ? "" : tags.trim();
        if (requirementRef == null || requirementRef.isBlank()) {
            return t;
        }
        Set<String> existing = new LinkedHashSet<>();
        for (String x : t.split("[,;]")) {
            if (!x.isBlank()) existing.add(x.trim());
        }
        for (String ref : requirementRef.split("[,;]")) {
            String r = ref.trim().replaceAll("\\s+", "-");
            if (r.isEmpty()) continue;
            String token = r.toLowerCase(Locale.ROOT).startsWith(PREFIX) ? r : PREFIX + r;
            existing.add(token);
        }
        return String.join(", ", existing);
    }

    public static List<String> refsOf(String tags) {
        List<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (String x : tags.split("[,;]")) {
            String v = x.trim();
            if (v.toLowerCase(Locale.ROOT).startsWith(PREFIX) && v.length() > PREFIX.length()) {
                out.add(v.substring(PREFIX.length()));
            }
        }
        return out;
    }

    /** requirementRef → tcIds, plus the cases that trace to nothing under key "" . */
    public static Map<String, List<String>> matrix(List<ManualTestCase> cases) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (ManualTestCase c : cases == null ? List.<ManualTestCase>of() : cases) {
            List<String> refs = refsOf(c.tags());
            if (refs.isEmpty()) {
                m.computeIfAbsent("", k -> new ArrayList<>()).add(c.tcId());
            }
            for (String r : refs) {
                m.computeIfAbsent(r, k -> new ArrayList<>()).add(c.tcId());
            }
        }
        return m;
    }
}
