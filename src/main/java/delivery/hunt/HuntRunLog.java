package delivery.hunt;

import utils.LogsManager;

import java.util.Locale;
import java.util.Map;

/** Console + file logging for live Bug Hunter (via LogsManager → CMD + logs.log). */
public final class HuntRunLog {
    private HuntRunLog() {
    }

    public static void info(String message) {
        LogsManager.info("HUNT: " + (message == null ? "" : message));
    }

    public static void warn(String message) {
        LogsManager.warn("HUNT: " + (message == null ? "" : message));
    }

    public static void cycleStart(int cycle, int ceiling, String url, String strategyHint) {
        info("cycle " + cycle + "/" + ceiling
                + " url=" + abbreviate(url, 120)
                + (strategyHint == null || strategyHint.isBlank() ? "" : " strategy=" + abbreviate(strategyHint, 80)));
    }

    public static void planner(int cycle, String decision, int actionCount, String rationale) {
        info("cycle " + cycle + " planner decision=" + decision
                + " actions=" + actionCount
                + (rationale == null || rationale.isBlank() ? "" : " rationale=" + abbreviate(rationale, 160)));
    }

    public static void action(Map<String, Object> row) {
        if (row == null) {
            return;
        }
        String type = str(row.get("type"));
        String status = str(row.get("status"));
        String detail = detailOf(row);
        String reason = str(row.get("reason"));
        String line = "action " + type + " -> " + status
                + (detail.isBlank() ? "" : " -- " + detail)
                + (!reason.isBlank() && !"ok".equalsIgnoreCase(status) ? " (" + abbreviate(reason, 120) + ")" : "");
        if ("ok".equalsIgnoreCase(status)) {
            info(line);
        } else {
            warn(line);
        }
    }

    public static void cycleDone(int cycle, int bugsSoFar, int scenariosSoFar) {
        info("cycle " + cycle + " done -- bugs=" + bugsSoFar + " scenarios=" + scenariosSoFar);
    }

    public static void finished(String stopReason, int cycles, int bugs, int scenarios) {
        info("finished stop=" + stopReason + " cycles=" + cycles
                + " bugs=" + bugs + " scenarios=" + scenarios);
    }

    private static String detailOf(Map<String, Object> row) {
        String type = str(row.get("type")).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "navigate" -> "url=" + str(row.get("url"));
            case "restart_browser" -> "login=" + str(row.get("login")) + " url=" + str(row.get("url"));
            case "type" -> "locator=" + locatorOf(row) + " value=" + abbreviate(str(row.get("value")), 40);
            case "click", "clear", "assert_visible" -> "locator=" + locatorOf(row);
            case "assert_text" -> "text=" + abbreviate(
                    str(row.get("text")).isBlank() ? str(row.get("expected")) : str(row.get("text")), 60);
            case "wait" -> "ms=" + str(row.get("ms"));
            case "execute_js" -> "script=" + abbreviate(jsOf(row), 80);
            case "back", "forward", "refresh" -> "";
            case "cap" -> abbreviate(str(row.get("reason")), 100);
            default -> "";
        };
    }

    private static String locatorOf(Map<String, Object> row) {
        String loc = str(row.get("locator"));
        if (loc.isBlank()) {
            loc = str(row.get("locatorValue"));
        }
        return abbreviate(loc, 80);
    }

    private static String jsOf(Map<String, Object> row) {
        String script = str(row.get("script"));
        if (script.isBlank()) {
            script = str(row.get("code"));
        }
        if (script.isBlank()) {
            script = str(row.get("js"));
        }
        return script;
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String oneLine = s.replace('\r', ' ').replace('\n', ' ').trim();
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max) + "...";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
