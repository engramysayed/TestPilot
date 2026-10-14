package delivery.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** P1: compact ≤5-action mini-script for each admitted bug row. */
public final class HuntReproScript {
    public static final int MAX_ACTIONS = 5;

    private HuntReproScript() {}

    public static List<String> fromActionLog(List<Map<String, Object>> actionLog) {
        List<String> out = new ArrayList<>();
        if (actionLog == null) return out;
        for (Map<String, Object> row : actionLog) {
            if (out.size() >= MAX_ACTIONS) break;
            String line = line(row);
            if (!line.isBlank()) out.add(line);
        }
        return out;
    }

    public static String format(List<String> steps) {
        if (steps == null || steps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String s : steps) {
            sb.append(i++).append(". ").append(s).append('\n');
        }
        return sb.toString().trim();
    }

    static String line(Map<String, Object> row) {
        String type = str(row.get("type")).toLowerCase(Locale.ROOT);
        String status = str(row.get("status"));
        return switch (type) {
            case "navigate" -> "navigate " + str(row.get("url")) + mark(status);
            case "click", "clear", "assert_visible" -> type + " " + locator(row) + mark(status);
            case "type" -> "type " + locator(row) + " = (redacted)" + mark(status);
            case "assert_text" -> "assert_text " + abbreviate(str(row.get("text")).isBlank()
                    ? str(row.get("expected")) : str(row.get("text")), 40) + mark(status);
            case "wait" -> "wait " + str(row.get("ms")) + "ms" + mark(status);
            case "back", "forward", "refresh" -> type + mark(status);
            case "restart_browser" -> "restart_browser" + mark(status);
            default -> type.isBlank() ? "" : type + mark(status);
        };
    }

    private static String locator(Map<String, Object> row) {
        String loc = str(row.get("locator"));
        if (loc.isBlank()) loc = str(row.get("locatorValue"));
        return loc.isBlank() ? "(no locator)" : loc;
    }

    private static String mark(String status) {
        if (status == null || status.isBlank() || "ok".equalsIgnoreCase(status)) return "";
        return " [" + status + "]";
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
