package delivery.hunt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fingerprint-based bug dedupe across hunt cycles. */
public final class HuntBugDedupe {
    private HuntBugDedupe() {
    }

    public static String fingerprint(Map<String, Object> bug) {
        if (bug == null) {
            return "";
        }
        String title = normalize(str(bug.get("title")));
        String sev = normalize(str(bug.get("severity")));
        if (sev.length() > 24 || sev.contains(" ")) {
            // polluted severity field — ignore
            sev = "";
        }
        return title + "|" + sev;
    }

    public static boolean addUnique(List<Map<String, Object>> allBugs, Map<String, Object> bug) {
        if (allBugs == null || bug == null) {
            return false;
        }
        String fp = fingerprint(bug);
        if (fp.isBlank()) {
            allBugs.add(bug);
            return true;
        }
        for (Map<String, Object> existing : allBugs) {
            if (fp.equals(fingerprint(existing))) {
                Object n = existing.get("dupCount");
                int d = 1;
                if (n instanceof Number num) {
                    d = num.intValue();
                }
                existing.put("dupCount", d + 1);
                return false;
            }
        }
        Map<String, Object> copy = new LinkedHashMap<>(bug);
        copy.putIfAbsent("dupCount", 1);
        allBugs.add(copy);
        return true;
    }

    public static Map<String, Object> stats(List<Map<String, Object>> allBugs, int attempted) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unique", allBugs == null ? 0 : allBugs.size());
        m.put("attempted", attempted);
        m.put("dropped", Math.max(0, attempted - (allBugs == null ? 0 : allBugs.size())));
        return m;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
