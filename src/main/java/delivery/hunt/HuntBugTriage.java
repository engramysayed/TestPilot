package delivery.hunt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * End-of-hunt mechanical apply of planner triage (keep / drop / merge).
 * When unsure, callers should prefer keep — this class only drops explicit matches.
 */
public final class HuntBugTriage {
    private HuntBugTriage() {
    }

    public record Decision(
            List<String> keepTitles,
            List<Map<String, String>> drops,
            List<Merge> merges,
            String rawJson
    ) {
        public record Merge(String into, List<String> from, String reason) {
        }
    }

    public static Decision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty triage json");
        }
        String trimmed = HuntPlannerDecision.extractJsonObject(HuntPlannerDecision.sanitizePlannerText(raw));
        JSONObject o = new JSONObject(trimmed);
        List<String> keep = titlesFrom(o.optJSONArray("keep"));
        List<Map<String, String>> drops = new ArrayList<>();
        JSONArray dropArr = o.optJSONArray("drop");
        if (dropArr != null) {
            for (int i = 0; i < dropArr.length(); i++) {
                Object item = dropArr.get(i);
                Map<String, String> row = new LinkedHashMap<>();
                if (item instanceof JSONObject jo) {
                    row.put("title", jo.optString("title", ""));
                    row.put("reason", jo.optString("reason", ""));
                } else {
                    row.put("title", String.valueOf(item));
                    row.put("reason", "");
                }
                drops.add(row);
            }
        }
        List<Decision.Merge> merges = new ArrayList<>();
        JSONArray mergeArr = o.optJSONArray("merge");
        if (mergeArr != null) {
            for (int i = 0; i < mergeArr.length(); i++) {
                JSONObject m = mergeArr.optJSONObject(i);
                if (m == null) {
                    continue;
                }
                List<String> from = titlesFrom(m.optJSONArray("from"));
                merges.add(new Decision.Merge(m.optString("into", ""), from, m.optString("reason", "")));
            }
        }
        return new Decision(keep, drops, merges, trimmed);
    }

    public static List<Map<String, Object>> apply(List<Map<String, Object>> bugs, Decision decision) {
        if (bugs == null || bugs.isEmpty()) {
            return List.of();
        }
        if (decision == null) {
            return new ArrayList<>(bugs);
        }
        Set<String> dropFps = new LinkedHashSet<>();
        for (Map<String, String> d : decision.drops()) {
            dropFps.add(normTitle(d.get("title")));
        }
        // Merges: drop "from" titles (into kept)
        for (Decision.Merge m : decision.merges()) {
            for (String f : m.from()) {
                dropFps.add(normTitle(f));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> bug : bugs) {
            String t = normTitle(String.valueOf(bug.get("title")));
            if (dropFps.contains(t)) {
                continue;
            }
            out.add(new LinkedHashMap<>(bug));
        }
        return out;
    }

    public static String triagePrompt(List<Map<String, Object>> bugs, String journalSlice, String coverageSlice) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are triaging Bug Hunter findings. Return ONLY JSON:\n");
        sb.append("{\"keep\":[{\"title\":\"...\",\"reason\":\"...\"}],");
        sb.append("\"drop\":[{\"title\":\"...\",\"reason\":\"noise|duplicate|oracle_false|flake\"}],");
        sb.append("\"merge\":[{\"into\":\"...\",\"from\":[\"...\"],\"reason\":\"...\"}]}\n");
        sb.append("Rules: do not invent new bugs; when unsure KEEP; drop oracle noise,");
        sb.append(" duplicates, timing flakes, and unexpected-login-URL on login features.\n\n");
        sb.append("## Candidates\n");
        int i = 1;
        for (Map<String, Object> b : bugs == null ? List.<Map<String, Object>>of() : bugs) {
            sb.append(i++).append(". ").append(b.get("title"))
                    .append(" | sev=").append(b.get("severity"))
                    .append(" | dup=").append(b.getOrDefault("dupCount", 1))
                    .append('\n');
        }
        sb.append("\n## Journal (tail)\n").append(journalSlice == null ? "" : journalSlice).append('\n');
        sb.append("\n## Coverage\n").append(coverageSlice == null ? "" : coverageSlice).append('\n');
        return sb.toString();
    }

    private static List<String> titlesFrom(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.get(i);
            if (item instanceof JSONObject jo) {
                out.add(jo.optString("title", ""));
            } else {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static String normTitle(String t) {
        if (t == null) {
            return "";
        }
        return t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
