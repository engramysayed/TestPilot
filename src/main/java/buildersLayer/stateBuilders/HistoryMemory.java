package buildersLayer.stateBuilders;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public final class HistoryMemory {
    private static String lastKnownUrl = "";

    private static final JSONArray recentCycles = new JSONArray();
    //linkedHashSet keeps insertion order, so oldest/newest order stays predictable.
    private static final LinkedHashSet<String> completedStepSet = new LinkedHashSet<>();
    //linkedHashMap lets us quickly update retryCount for the same failure key.
    private static final LinkedHashMap<String, JSONObject> failedAttemptMap = new LinkedHashMap<>();

    private static final int MAX_RECENT_CYCLES = 5;
    private static final int MAX_COMPLETED_STEPS = 60;
    private static final int MAX_FAILED_ATTEMPTS = 30;


    public static String getHistorySummaryForPlanner() {
        JSONObject history = new JSONObject();
        history.put("recentCycles", new JSONArray(recentCycles.toList()));
        history.put("completedSteps", new JSONArray(completedStepSet));
        history.put("failedAttempts", new JSONArray(failedAttemptMap.values()));
        history.put("lastKnownUrl", safe(lastKnownUrl));
        history.put("lastScreenshotRef", safe(StateVars.getLastScreenshotRef()));
        return history.toString();
    }

    public static void updateHistoryMemory(int cycleId, String batchDetails, JSONArray executedStepsJson,
                                           String currentUrl, String screenshotRef) {
        int failedCount = 0;
        int total = 0;
        if (executedStepsJson != null) {
            total = executedStepsJson.length();
        }

        if (executedStepsJson != null) {
            for (int i = 0; i < executedStepsJson.length(); i++) {
                JSONObject step = executedStepsJson.getJSONObject(i);
                String action = step.optString("action", "");
                String selector = step.optString("selector", "");
                String value = step.optString("value", "");
                JSONObject result = step.optJSONObject("result");
                boolean success = false;
                String reason = "";
                if (result != null) {
                    success = result.optBoolean("success", false);
                    reason = result.optString("message", "");
                }

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

        lastKnownUrl = safe(currentUrl);
        if (screenshotRef != null) {
            StateVars.setLastScreenshotRef(screenshotRef);
        }
    }

    private static String buildStepFingerprint(String action, String selector, String value) {
        String a = safe(action);
        String s = safe(selector);
        String v = safe(value);
        if (v.isBlank()) {
            return a + "|" + s;
        }
        return a + "|" + s + "|value:" + shorten(v, 120);
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
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }
}
