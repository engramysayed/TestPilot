package delivery.hunt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HuntPlannerDecision {
    public enum Decision { CONTINUE, FINISH }

    private final Decision decision;
    private final String rationale;
    private final List<Map<String, Object>> actions;
    private final List<Map<String, Object>> bugs;
    private final List<Map<String, Object>> scenarios;
    private final String rawJson;

    public HuntPlannerDecision(Decision decision, String rationale,
                               List<Map<String, Object>> actions,
                               List<Map<String, Object>> bugs,
                               List<Map<String, Object>> scenarios,
                               String rawJson) {
        this.decision = decision;
        this.rationale = rationale == null ? "" : rationale;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.bugs = bugs == null ? List.of() : List.copyOf(bugs);
        this.scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public Decision decision() { return decision; }
    public String rationale() { return rationale; }
    public List<Map<String, Object>> actions() { return actions; }
    public List<Map<String, Object>> bugs() { return bugs; }
    public List<Map<String, Object>> scenarios() { return scenarios; }
    public String rawJson() { return rawJson; }

    public static HuntPlannerDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty planner JSON");
        }
        String cleaned = sanitizePlannerText(raw);
        String trimmed = stripFence(cleaned.trim());
        trimmed = extractJsonObject(trimmed);
        JSONObject o;
        try {
            o = new JSONObject(trimmed);
        } catch (org.json.JSONException first) {
            // Second pass: drop leftover control chars that still break strings.
            trimmed = sanitizePlannerText(trimmed).replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
            trimmed = extractJsonObject(trimmed);
            o = new JSONObject(trimmed);
        }
        String d = o.optString("decision", "continue").trim().toLowerCase(Locale.ROOT);
        Decision decision = "finish".equals(d) ? Decision.FINISH : Decision.CONTINUE;
        return new HuntPlannerDecision(
                decision,
                o.optString("rationale", ""),
                toMapList(o.optJSONArray("actions")),
                toMapList(o.optJSONArray("bugs")),
                toMapList(o.optJSONArray("scenarios")),
                trimmed
        );
    }

    /** Remove NULs / illegal controls that small local models sometimes emit inside JSON strings. */
    static String sanitizePlannerText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == 0) {
                continue;
            }
            // Keep tab/LF/CR; drop other C0 controls that break org.json string parsing.
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Prefer the outermost {...} when the model wraps JSON in prose. */
    static String extractJsonObject(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1).trim();
        }
        return s.trim();
    }

    public static HuntPlannerDecision finishDryRunSeed(int scenarioCap) {
        List<Map<String, Object>> bugs = new ArrayList<>();
        Map<String, Object> bug = new LinkedHashMap<>();
        bug.put("title", "Dry-run sample defect");
        bug.put("severity", "minor");
        bug.put("repro", "1. Open feature under test\n2. Trigger edge input\n3. Observe unexpected state");
        bug.put("expected", "Feature handles edge input safely");
        bug.put("actual", "Dry-run placeholder — replace with live hunter findings");
        bugs.add(bug);

        List<Map<String, Object>> scenarios = new ArrayList<>();
        if (scenarioCap > 0) {
            Map<String, Object> sc = new LinkedHashMap<>();
            sc.put("title", "Edge: empty required field submit");
            sc.put("steps", "1. Open the form\n2. Leave required fields empty\n3. Click submit\n4. Confirm validation message");
            sc.put("expected", "Clear validation without server error");
            sc.put("tags", "edge,validation");
            scenarios.add(sc);
        }
        JSONObject raw = new JSONObject();
        raw.put("decision", "finish");
        raw.put("rationale", "Dry-run hunt complete");
        raw.put("actions", new JSONArray());
        raw.put("bugs", new JSONArray(bugs));
        raw.put("scenarios", new JSONArray(scenarios));
        return new HuntPlannerDecision(Decision.FINISH, "Dry-run hunt complete", List.of(), bugs, scenarios, raw.toString(2));
    }

    private static String stripFence(String s) {
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    private static List<Map<String, Object>> toMapList(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            Object el = arr.get(i);
            if (el instanceof JSONObject jo) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (String key : jo.keySet()) {
                    m.put(key, jo.get(key));
                }
                out.add(m);
            }
        }
        return out;
    }
}
