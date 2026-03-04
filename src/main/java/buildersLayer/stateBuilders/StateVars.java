package buildersLayer.stateBuilders;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.Config.AppConfigProvider;
import utils.ScenarioReader;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class StateVars {
    private  static String lastScreenshotRef="step_0.png";
    private static String stateJson;
    private static final String scenario = ScenarioReader.getScenario();
    private static final int HTML_MAX_CHARS = AppConfigProvider.get().htmlMaxChars();
    private  static final StringBuilder runningSummary = new StringBuilder();
    private static String lastKnownUrl = "";

    private static final JSONArray recentCycles = new JSONArray();
    private static final LinkedHashSet<String> completedStepSet = new LinkedHashSet<>();
    private static final LinkedHashMap<String, JSONObject> failedAttemptMap = new LinkedHashMap<>();

    private static final int MAX_RECENT_CYCLES = 5;
    private static final int MAX_COMPLETED_STEPS = 60;
    private static final int MAX_FAILED_ATTEMPTS = 30;

    //getters
    public static String getLastScreenshotRef(){return lastScreenshotRef;}
    public static String getStateJson(){return stateJson;}
    public static String getScenario(){return scenario;}
    public static int getHTML_MAX_CHARS(){return HTML_MAX_CHARS;}
    public static StringBuilder getRunningSummary(){return runningSummary;}
    public static String getHistorySummaryForPlanner() {
        JSONObject root = new JSONObject();
        root.put("recentCycles", new JSONArray(recentCycles.toList()));
        root.put("completedSteps", new JSONArray(completedStepSet));
        root.put("failedAttempts", new JSONArray(failedAttemptMap.values()));
        root.put("lastKnownUrl", lastKnownUrl == null ? "" : lastKnownUrl);
        root.put("lastScreenshotRef", lastScreenshotRef == null ? "" : lastScreenshotRef);
        return root.toString();
    }


    //setters
    public static void setLastScreenshotRef(String lastScreenshot){lastScreenshotRef=lastScreenshot;}
    public static void setRunningSummary(StringBuilder Summary){runningSummary.append(Summary);}
    public static void setStateJson(String state){stateJson=state;}

    public static void updateHistoryMemory(int cycleId, String batchDetails, JSONArray executedStepsJson,
                                           String currentUrl, String screenshotRef) {
        int failedCount = 0;
        int total = executedStepsJson == null ? 0 : executedStepsJson.length();

        if (executedStepsJson != null) {
            for (int i = 0; i < executedStepsJson.length(); i++) {
                JSONObject step = executedStepsJson.getJSONObject(i);
                String action = step.optString("action", "");
                String selector = step.optString("selector", "");
                String value = step.optString("value", "");
                JSONObject result = step.optJSONObject("result");
                boolean success = result != null && result.optBoolean("success", false);
                String reason = result == null ? "" : result.optString("message", "");

                String fingerprint = buildStepFingerprint(action, selector, value);
                if (success) {
                    addCompletedStep(fingerprint);
                } else {
                    failedCount++;
                    addFailedAttempt(action, selector, reason);
                }
            }
        }

        String cycleResult;
        if (total == 0 || failedCount == total) {
            cycleResult = "failed";
        } else if (failedCount == 0) {
            cycleResult = "success";
        } else {
            cycleResult = "partial_success";
        }

        JSONObject cycle = new JSONObject();
        cycle.put("cycleId", cycleId);
        cycle.put("batchDetails", safe(batchDetails));
        cycle.put("result", cycleResult);
        recentCycles.put(cycle);
        trimRecentCycles();

        lastKnownUrl = currentUrl == null ? "" : currentUrl;
        if (screenshotRef != null) {
            lastScreenshotRef = screenshotRef;
        }
    }

    private static String buildStepFingerprint(String action, String selector, String value) {
        String actionVar = safe(action);
        String selectorVar = safe(selector);
        String valueVar = safe(value);
        if (valueVar.isBlank()) {
            return actionVar + "|" + selectorVar;
        }
        return actionVar + "|" + selectorVar + "|value:" + shorten(valueVar, 120);
    }

    private static void addCompletedStep(String fingerprint) {
        if (fingerprint.isBlank()) {
            return;
        }
        completedStepSet.add(fingerprint);
        while (completedStepSet.size() > MAX_COMPLETED_STEPS) {
            Iterator<String> it = completedStepSet.iterator();
            if (!it.hasNext()) {
                break;
            }
            it.next();
            it.remove();
        }
    }

    private static void addFailedAttempt(String action, String selector, String reason) {
        String key = safe(action) + "|" + safe(selector);
        if (key.isBlank()) {
            return;
        }
        JSONObject existing = failedAttemptMap.get(key);
        if (existing == null) {
            JSONObject item = new JSONObject();
            item.put("action", safe(action));
            item.put("selector", safe(selector));
            item.put("reason", shorten(safe(reason), 220));
            item.put("retryCount", 1);
            failedAttemptMap.put(key, item);
        } else {
            existing.put("reason", shorten(safe(reason), 220));
            existing.put("retryCount", existing.optInt("retryCount", 1) + 1);
        }
        while (failedAttemptMap.size() > MAX_FAILED_ATTEMPTS) {
            String firstKey = failedAttemptMap.keySet().iterator().next();
            failedAttemptMap.remove(firstKey);
        }
    }

    private static void trimRecentCycles() {
        while (recentCycles.length() > MAX_RECENT_CYCLES) {
            recentCycles.remove(0);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

}
