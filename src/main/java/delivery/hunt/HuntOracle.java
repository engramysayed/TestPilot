package delivery.hunt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Post-action oracle: promotes network failures, failed asserts, alerts, blank main,
 * and unexpected URL redirects into bug drafts when the planner stays silent or misses them.
 */
public final class HuntOracle {
    /** Visible main/body text shorter than this after navigate/click → blank-main bug. */
    public static final int BLANK_MAIN_CHARS = 40;
    private static final Pattern UNEXPECTED_URL = Pattern.compile(
            "(?i)(login|signin|sign-in|error|404|403|denied|unauthorized)");

    private HuntOracle() {
    }

    /** Page-state signals gathered after the action batch (optional). */
    public record PageSignals(
            String currentUrl,
            int mainTextLength,
            List<String> visitedUrlsBeforeCycle,
            String strategyMode,
            boolean lastActionWasNavigateOrClick
    ) {
        public static PageSignals empty() {
            return new PageSignals("", -1, List.of(), "", false);
        }
    }

    public static List<Map<String, Object>> collect(
            int cycle,
            List<Map<String, Object>> networkFailures,
            List<String> alertTexts,
            List<Map<String, Object>> actionLog,
            String journalRepro,
            List<Map<String, Object>> plannerBugs
    ) {
        return collect(cycle, networkFailures, alertTexts, actionLog, journalRepro, plannerBugs,
                PageSignals.empty());
    }

    public static List<Map<String, Object>> collect(
            int cycle,
            List<Map<String, Object>> networkFailures,
            List<String> alertTexts,
            List<Map<String, Object>> actionLog,
            String journalRepro,
            List<Map<String, Object>> plannerBugs,
            PageSignals page
    ) {
        List<Map<String, Object>> drafts = new ArrayList<>();
        List<Map<String, Object>> planner = plannerBugs == null ? List.of() : plannerBugs;
        String repro = journalRepro == null ? "" : journalRepro;
        PageSignals signals = page == null ? PageSignals.empty() : page;

        if (planner.isEmpty()) {
            drafts.addAll(bugsFromNetworkFailures(networkFailures, repro));
        }
        drafts.addAll(bugsFromFailedAsserts(actionLog, planner, repro));
        drafts.addAll(bugsFromAlertsAfterFail(alertTexts, actionLog, repro));
        drafts.addAll(bugsFromBlankMain(signals, actionLog, planner, repro));
        drafts.addAll(bugsFromUnexpectedUrl(signals, planner, repro));
        tagOracleSource(drafts, cycle);
        return drafts;
    }

    /** Snapshot for cycles/cycle-NN/oracle.json. */
    public static Map<String, Object> signalSnapshot(
            List<Map<String, Object>> networkFailures,
            List<String> alertTexts,
            PageSignals page,
            List<Map<String, Object>> oracleDrafts
    ) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("networkFailures", networkFailures == null ? List.of() : networkFailures);
        snap.put("alerts", alertTexts == null ? List.of() : alertTexts);
        Map<String, Object> pageMap = new LinkedHashMap<>();
        PageSignals p = page == null ? PageSignals.empty() : page;
        pageMap.put("currentUrl", p.currentUrl());
        pageMap.put("mainTextLength", p.mainTextLength());
        pageMap.put("strategyMode", p.strategyMode());
        pageMap.put("lastActionWasNavigateOrClick", p.lastActionWasNavigateOrClick());
        snap.put("page", pageMap);
        snap.put("draftsEmitted", oracleDrafts == null ? 0 : oracleDrafts.size());
        return snap;
    }

    public static int mainTextLength(String slimHtml) {
        if (slimHtml == null || slimHtml.isBlank()) {
            return 0;
        }
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(slimHtml);
            org.jsoup.nodes.Element main = doc.selectFirst("main, [role=main]");
            String text = main != null ? main.text() : (doc.body() != null ? doc.body().text() : "");
            return text == null ? 0 : text.trim().length();
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean lastActionNavigateOrClick(List<Map<String, Object>> actionLog) {
        if (actionLog == null || actionLog.isEmpty()) {
            return false;
        }
        for (int i = actionLog.size() - 1; i >= 0; i--) {
            String type = str(actionLog.get(i).get("type")).toLowerCase(Locale.ROOT);
            if ("cap".equals(type) || type.isBlank()) {
                continue;
            }
            return "navigate".equals(type) || "click".equals(type);
        }
        return false;
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
            if (isHttpError(fail)) {
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
                continue;
            }
            if (isNetworkLoadFailure(fail)) {
                String err = str(fail.get("errorText"));
                if (err.isBlank()) {
                    err = str(fail.get("type"));
                }
                String url = str(fail.get("url"));
                Map<String, Object> bug = new LinkedHashMap<>();
                bug.put("title", "Network load failed" + (url.isBlank() ? "" : ": " + shortenUrl(url)));
                bug.put("severity", "major");
                bug.put("expected", "Resource loads successfully");
                bug.put("actual", err + (url.isBlank() ? "" : " url=" + url));
                bug.put("repro", repro);
                bugs.add(bug);
            }
        }
        return bugs;
    }

    private static List<Map<String, Object>> bugsFromBlankMain(
            PageSignals signals,
            List<Map<String, Object>> actionLog,
            List<Map<String, Object>> plannerBugs,
            String repro
    ) {
        if (signals.mainTextLength() < 0 || !signals.lastActionWasNavigateOrClick()) {
            return List.of();
        }
        if (signals.mainTextLength() >= BLANK_MAIN_CHARS) {
            return List.of();
        }
        if (actionLog == null || actionLog.isEmpty()) {
            return List.of();
        }
        String title = "Blank or near-empty main content after navigation";
        if (plannerAlreadyHas(plannerBugs, title, "blank")) {
            return List.of();
        }
        Map<String, Object> bug = new LinkedHashMap<>();
        bug.put("title", title);
        bug.put("severity", "major");
        bug.put("expected", "Main content with visible text after navigate/click");
        bug.put("actual", "main/body text length=" + signals.mainTextLength()
                + " (threshold " + BLANK_MAIN_CHARS + ") url=" + signals.currentUrl());
        bug.put("repro", repro);
        return List.of(bug);
    }

    private static List<Map<String, Object>> bugsFromUnexpectedUrl(
            PageSignals signals,
            List<Map<String, Object>> plannerBugs,
            String repro
    ) {
        String url = signals.currentUrl() == null ? "" : signals.currentUrl().trim();
        if (url.isBlank() || !UNEXPECTED_URL.matcher(url).find()) {
            return List.of();
        }
        String mode = signals.strategyMode() == null ? "" : signals.strategyMode().toLowerCase(Locale.ROOT);
        if (mode.contains("session")) {
            return List.of();
        }
        List<String> prior = signals.visitedUrlsBeforeCycle() == null
                ? List.of() : signals.visitedUrlsBeforeCycle();
        boolean hadOther = false;
        for (String prev : prior) {
            if (prev == null || prev.isBlank() || prev.equalsIgnoreCase(url)) {
                continue;
            }
            if (!UNEXPECTED_URL.matcher(prev).find()) {
                hadOther = true;
                break;
            }
        }
        if (!hadOther) {
            return List.of();
        }
        String title = "Unexpected redirect to login/error URL";
        if (plannerAlreadyHas(plannerBugs, title, url)) {
            return List.of();
        }
        Map<String, Object> bug = new LinkedHashMap<>();
        bug.put("title", title);
        bug.put("severity", "major");
        bug.put("expected", "Stay on feature URL (not login/error) unless session strategy");
        bug.put("actual", url);
        bug.put("repro", repro);
        return List.of(bug);
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

    private static boolean isNetworkLoadFailure(Map<String, Object> fail) {
        String type = str(fail.get("type")).toLowerCase(Locale.ROOT);
        String err = str(fail.get("errorText")).toLowerCase(Locale.ROOT);
        if ("loading_failed".equals(type) || "net_error".equals(type)) {
            return true;
        }
        return err.contains("net::err") || err.startsWith("net::");
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
