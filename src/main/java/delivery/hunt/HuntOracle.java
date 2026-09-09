package delivery.hunt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Post-action oracle: promotes network failures, failed asserts, and alert signals into bug drafts
 * when the planner stays silent or misses them.
 */
public final class HuntOracle {
    private HuntOracle() {
    }

    public static List<Map<String, Object>> collect(
            int cycle,
            List<Map<String, Object>> networkFailures,
            List<String> alertTexts,
            List<Map<String, Object>> actionLog,
            String journalRepro,
            List<Map<String, Object>> plannerBugs
    ) {
        List<Map<String, Object>> drafts = new ArrayList<>();
        List<Map<String, Object>> planner = plannerBugs == null ? List.of() : plannerBugs;
        String repro = journalRepro == null ? "" : journalRepro;

        if (planner.isEmpty()) {
            drafts.addAll(bugsFromNetworkFailures(networkFailures, repro));
        }
        drafts.addAll(bugsFromFailedAsserts(actionLog, planner, repro));
        drafts.addAll(bugsFromAlertsAfterFail(alertTexts, actionLog, repro));
        tagOracleSource(drafts, cycle);
        return drafts;
    }

    private static List<Map<String, Object>> bugsFromNetworkFailures(
            List<Map<String, Object>> networkFailures,
            String repro
    ) {
        List<Map<String, Object>> bugs = new ArrayList<>();
        if (networkFailures == null || networkFailures.isEmpty()) {
            return bugs;
        }
        for (Map<String, Object> fail : networkFailures) {
            if (!isHttpError(fail)) {
                continue;
            }
            int status = toInt(fail.get("status"));
            if (status < 400) {
                continue;
            }
            String url = str(fail.get("url"));
            Map<String, Object> bug = new LinkedHashMap<>();
            bug.put("title", "HTTP " + status + " on " + shortenUrl(url));
            bug.put("severity", status >= 500 ? "major" : "minor");
            bug.put("expected", "Successful response (2xx) for " + shortenUrl(url));
            bug.put("actual", "HTTP " + status + (url.isBlank() ? "" : " on " + url));
            bug.put("repro", repro);
            bugs.add(bug);
        }
        return bugs;
    }

    private static List<Map<String, Object>> bugsFromFailedAsserts(
            List<Map<String, Object>> actionLog,
            List<Map<String, Object>> plannerBugs,
            String repro
    ) {
        List<Map<String, Object>> bugs = new ArrayList<>();
        if (actionLog == null || actionLog.isEmpty()) {
            return bugs;
        }
        for (Map<String, Object> row : actionLog) {
            String type = str(row.get("type"));
            if (!"fail".equals(str(row.get("status"))) || !type.startsWith("assert_")) {
                continue;
            }
            String title = "Assert failed: " + type;
            if (plannerAlreadyHas(plannerBugs, title, str(row.get("reason")))) {
                continue;
            }
            Map<String, Object> bug = new LinkedHashMap<>();
            bug.put("title", title);
            bug.put("severity", "major");
            bug.put("repro", repro.isBlank() ? "Planner action " + type + " failed during hunt" : repro);
            bug.put("expected", str(row.get("expected")).isBlank() ? "Assertion to pass" : str(row.get("expected")));
            bug.put("actual", str(row.get("reason")));
            bugs.add(bug);
        }
        return bugs;
    }

    private static List<Map<String, Object>> bugsFromAlertsAfterFail(
            List<String> alertTexts,
            List<Map<String, Object>> actionLog,
            String repro
    ) {
        if (alertTexts == null || alertTexts.isEmpty() || !hasFailAction(actionLog)) {
            return List.of();
        }
        String alertSummary = String.join("; ", alertTexts);
        Map<String, Object> bug = new LinkedHashMap<>();
        bug.put("title", "Alert shown after failed action");
        bug.put("severity", "major");
        bug.put("expected", "No error alert after a successful interaction");
        bug.put("actual", alertSummary);
        bug.put("repro", repro);
        return List.of(bug);
    }

    private static boolean hasFailAction(List<Map<String, Object>> actionLog) {
        if (actionLog == null) {
            return false;
        }
        for (Map<String, Object> row : actionLog) {
            if ("fail".equals(str(row.get("status")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean plannerAlreadyHas(List<Map<String, Object>> plannerBugs, String title, String actual) {
        if (plannerBugs == null || plannerBugs.isEmpty()) {
            return false;
        }
        String titleLower = title.toLowerCase(Locale.ROOT);
        String actualLower = actual.toLowerCase(Locale.ROOT);
        for (Map<String, Object> bug : plannerBugs) {
            String pt = str(bug.get("title")).toLowerCase(Locale.ROOT);
            String pa = str(bug.get("actual")).toLowerCase(Locale.ROOT);
            if (pt.contains(titleLower) || titleLower.contains(pt)) {
                return true;
            }
            if (!actualLower.isBlank() && (pa.contains(actualLower) || actualLower.contains(pa))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHttpError(Map<String, Object> fail) {
        return "http_error".equals(str(fail.get("type")));
    }

    private static void tagOracleSource(List<Map<String, Object>> drafts, int cycle) {
        for (Map<String, Object> bug : drafts) {
            bug.put("oracleSource", true);
            if (!bug.containsKey("evidenceHint")) {
                bug.put("evidenceHint", "cycles/cycle-" + String.format(Locale.ROOT, "%02d", cycle)
                        + "/screenshot.png");
            }
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(str(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String shortenUrl(String url) {
        if (url.isBlank()) {
            return "request";
        }
        if (url.length() <= 80) {
            return url;
        }
        return url.substring(0, 77) + "...";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
