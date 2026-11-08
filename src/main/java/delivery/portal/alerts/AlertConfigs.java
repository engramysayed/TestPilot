package delivery.portal.alerts;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for alert-channel config (read env, parse CSV). Both channels did this inline. */
final class AlertConfigs {
    private AlertConfigs() {}

    /** First non-null, non-blank value in order; trimmed. Returns "" if none. */
    static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    /** Parse "a,b;c" → ["a","b","c"]. Trims and drops empty entries. Returns empty on null/blank. */
    static List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String p : csv.split("[,;]")) {
            String t = p == null ? "" : p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return List.copyOf(out);
    }
}
