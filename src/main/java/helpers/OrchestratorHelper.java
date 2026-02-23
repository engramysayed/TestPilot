package helpers;

import llmLayer.LLMPlanner;
import drivers.WebDriverFactory;
import executionLayer.SelectorParser;
import executionLayer.actionExecute;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import parsingLayer.JsonMapper;
import parsingLayer.HtmlSlimmer;
import utils.LogsManager;
import utils.PropertyReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static utils.FilesManager.createDirectory;

public class OrchestratorHelper {

    private final WebDriverFactory driver;
    private String llmResponse , stateJson , stepDetails , actionType , action , selector , value , scenario;
    private String message = "OK" , urlParam = "" , tabParam = "" , result
                               ,batchDetails = "" ,currentObservation = "",finalSummary = "";
    private int stepId , HTML_MAX_CHARS , generalWait , screenshotWait;
    private  boolean screenshot , stopTesting ,success = true , started = false, stopAfterBatch = false;
    private String lastScreenshotRef = "step_0.png";
    private List<String[]> plannedSteps = new ArrayList<>() ;
    private List<JSONObject> finalBugs = new ArrayList<>();
    private JSONArray executedStepsJson = new org.json.JSONArray();
    private final StringBuilder runningSummary = new StringBuilder();
    private Path summaryFile,bugsFile;

    public OrchestratorHelper(WebDriverFactory driver,int stepId,
                              String scenario ,int HTML_MAX_CHARS){
        this.driver = driver;
        this.stepId=stepId;
        this.scenario=scenario;
        this.HTML_MAX_CHARS=HTML_MAX_CHARS;
    }


    //main functions
    public void buildStartState(actionExecute executor, Path runFolder){
        if (started)
        {
            return;
        }
        started = true;
        summaryFile = runFolder.resolve("planner").resolve("running_summary.txt");
        bugsFile = runFolder.resolve("planner").resolve("bugs.txt");


        executor.takeScreenshot(runFolder, 0);
        lastScreenshotRef = "step_0.png";

        //if in iframe switch back to default
        driver.frames().switchToDefaultContent();

        stateJson = JsonMapper.buildPlannerStart(
                scenario,
                driver.browser().getCurrentUrl(),
                HtmlSlimmer.slim(driver.browser().getPageSource(), HTML_MAX_CHARS),
                lastScreenshotRef,
                runningSummary.toString()
        );
    }

    public OrchestratorHelper askLLM(LLMPlanner planner, int cycleId) {
        try {
            LogsManager.info("Requesting next batch from LLM. cycleId=" + cycleId);
            llmResponse = planner.getNextBatch(cycleId, stateJson, lastScreenshotRef);
        } catch (Exception e) {
            LogsManager.error("Error in LLM request " + e);
        }
        return this;
    }

    public void parsingBatch() {
        stopAfterBatch = false;
        if (llmResponse == null || llmResponse.isBlank()) {
            stopTesting = true;
            return;
        }
        String clean = JsonMapper.buildResult(llmResponse);
        JSONObject root = new JSONObject(clean);

        plannedSteps = JsonMapper.parseBatchToArrays(clean);


        boolean batchStop = root.optBoolean("stopTesting", false);
        stopAfterBatch = batchStop;
        JSONArray bugsArr = root.optJSONArray("bugs");
        if (stopAfterBatch) {
            finalBugs.clear();
            if (bugsArr != null) {
                for (int i = 0; i < bugsArr.length(); i++) {
                    finalBugs.add(bugsArr.getJSONObject(i));
                }
            }
        }


        if (batchStop && plannedSteps.isEmpty()) {
            stopTesting = true;
            return;
        }

        batchDetails = root.optString("batchDetails", "");
        finalSummary = root.optString("finalSummary", "");
        currentObservation = root.optString("currentObservation", "");



        LogsManager.info("LLM Batch details: " + batchDetails);
        LogsManager.info("LLM Batch size: " + plannedSteps.size());
    }

    public void buildUpdateState(){
        stateJson = JsonMapper.buildPlannerStart(
                scenario,
                driver.browser().getCurrentUrl(),
                HtmlSlimmer.slim(driver.browser().getPageSource(), HTML_MAX_CHARS),
                lastScreenshotRef,
                runningSummary.toString()
        );
    }


    public OrchestratorHelper setStepId(int stepId) {
        this.stepId = stepId;
        return this;
    }

    public OrchestratorHelper executeBatch(actionExecute executor,
                                           Path runFolder,
                                           int cycleId,
                                           int DEFAULT_WAIT,
                                           int DEFAULT_SCREENSHOT_WAIT) {

        executedStepsJson = new org.json.JSONArray();
        int i=0;
        for (String[] step : plannedSteps) {

            // map to existing fields (reuse your executeActions pipeline)
            stepId = Integer.parseInt(step[0]);
            stepDetails = step[1];
            actionType = step[2];
            action = step[3];
            selector = step[4];
            value = step[5];
            generalWait = validateTime(step[6], DEFAULT_WAIT);
            screenshotWait = validateTime(step[7], DEFAULT_SCREENSHOT_WAIT);
            screenshot = Boolean.parseBoolean(step[8]);
            stopTesting = Boolean.parseBoolean(step[9]);

            // reset per step
            success = true;
            message = "OK";
            storeVars(generalWait, screenshotWait);

            // execute (same as your executeActions but inline)
            try {
                String result = executor.checkAction(
                        actionType,
                        action,
                        value,
                        value,
                        getSelector(selector),
                        value
                );

                if (result == null || result.contains("false")) {
                    success = false;
                    message = "Action returned failure: " + result;
                } else {
                    message = "Action executed: " + result;
                }
                String shotName = "cycle_" + cycleId + "_step_" + (i + 1);
                screenShotChecker(executor, runFolder, shotName);


            } catch (Exception e) {
                success = false;
                message = e.getMessage();
            }

            // build executed step json
            JSONObject executed = new JSONObject();
            executed.put("stepId", stepId);
            executed.put("stepDetails", stepDetails);
            executed.put("actionType", actionType);
            executed.put("action", action);
            executed.put("selector", selector);
            executed.put("value", value);

            JSONObject res = new JSONObject();
            res.put("success", success);
            res.put("message", message);
            executed.put("result", res);

            executedStepsJson.put(executed);

            if (stopTesting) break;
            i++;
        }
        if (stopAfterBatch) {
            stopTesting = true;
        }
        appendCycleSummary(cycleId);
        saveRunningSummary();

        return this;
    }






    //minor functions
    private void screenShotChecker(actionExecute executor, Path runFolder, String shotName){
        try {
            if (screenshot) {
                if (screenshotWait > 0) {
                    TimeUnit.SECONDS.sleep(screenshotWait);
                }
                executor.takeScreenshot(runFolder, shotName);
                lastScreenshotRef = shotName + ".png";
            }
        } catch (Exception e) {
            LogsManager.error("Error taking screenshot " + e);
        }
    }

    private By getSelector(String selectors){

        return SelectorParser.toBy(selectors);
    }

    public boolean isStopTesting() {
        if (stopTesting) {
            LogsManager.info("stopTesting=true , Ending loop.");
        }else
        {
            LogsManager.info("stopTesting=false , Looping..");
        }
        return stopTesting;
    }

    private int validateTime(String givenTime, int defaultTime) {
        try {
            if (givenTime == null || givenTime.isBlank())
            {
                return defaultTime;
            }
            else {
                return Integer.parseInt(givenTime);
            }
        } catch (Exception e) {
            return defaultTime;
        }
    }

    private void storeVars(int time,int screenWaitTime){
        PropertyReader.setProperty("globalWait", String.valueOf(time));
        PropertyReader.setProperty("screenShotWait", String.valueOf(screenWaitTime));
    }

    public static Path createNewRunFolder() {
        String ts = getTimeStamp();

        Path runPath = Path.of(
                System.getProperty("user.dir"),
                "test-output",
                "runs",
                "run_" + ts
        );

        //Create main run directory
        createDirectory(runPath.toString());

        //Subfolders
        createDirectory(runPath.resolve("screenshots").toString());
        createDirectory(runPath.resolve("planner").toString());
        return runPath;
    }

    private static String getTimeStamp(){
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                .replace(":", "-").replace(" ", "_");
    }

    public void preStepsActions(actionExecute executor){
        try {
        storeVars(5,1);

        executor.elementAction("type",getSelector(PropertyReader.getProperty("userNameLocator"))
                ,PropertyReader.getProperty("USERNAME"));
        executor.elementAction("type",getSelector(PropertyReader.getProperty("passwordLocator"))
                ,PropertyReader.getProperty("PASSWORD"));
        executor.elementAction("click",getSelector(PropertyReader.getProperty("clickLocator")),"");

        TimeUnit.SECONDS.sleep(40);
        } catch (Exception e) {
            LogsManager.error("Error in preActions "+e);
        }

    }

    public void preStepsUrl(actionExecute executor){
        executor.browserAction("navigate",PropertyReader.getProperty("BASE_WEB"),"");
    }

    public String getFinalSummary() {
        return finalSummary;
    }

    public String getCurrentObservation() {
        return currentObservation;
    }

    private void appendCycleSummary(int cycleId) {
        try {
            StringBuilder sb = new StringBuilder();

            sb.append("==================================================\n");
            sb.append("Cycle ").append(cycleId)
                    .append(" | Batch: ").append(safe(batchDetails)).append("\n");

            for (int i = 0; i < executedStepsJson.length(); i++) {
                JSONObject step = executedStepsJson.getJSONObject(i);
                JSONObject res = step.optJSONObject("result");

                boolean ok = res != null && res.optBoolean("success", false);
                String msg = (res == null) ? "" : res.optString("message", "");

                sb.append("  ")
                        .append(icon(ok)).append(" Step ").append(step.optInt("stepId", i + 1))
                        .append(" | ").append(safe(step.optString("actionType", "")))
                        .append(" -> ").append(safe(step.optString("action", "")))
                        .append(" | ").append(safe(step.optString("selector", "")));

                // show value only when it matters (type, upload, dragDrop, frame switch)
                String v = step.optString("value", "");
                if (v != null && !v.isBlank()) {
                    sb.append(" | value=").append(safe(v));
                }

                if (!ok) {
                    sb.append("\n      ↳ ").append(shortenError(msg));
                }

                sb.append("\n");
            }

            sb.append("URL: ").append(safe(driver.browser().getCurrentUrl())).append("\n");
            sb.append("LastScreenshot: ").append(safe(lastScreenshotRef)).append("\n");

            runningSummary.append(sb);

        } catch (Exception e) {
            LogsManager.error("Failed to append cycle summary: " + e.getMessage());
        }
    }

    private void saveRunningSummary() {
        try {
            utils.FilesManager.writeFile(summaryFile, runningSummary.toString());
        } catch (Exception e) {
            LogsManager.error("Failed to save running summary: " + e.getMessage());
        }
    }

    public void recordBugs() {
        try {
            if (bugsFile == null) return;

            if (finalBugs.isEmpty()) {
                utils.FilesManager.writeFile(bugsFile, "No bugs reported by agent in this run.\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Bugs reported by agent:\n\n");

            for (JSONObject b : finalBugs) {
                sb.append("====================================\n");
                sb.append("ID: ").append(b.optString("id")).append("\n");
                sb.append("Title: ").append(b.optString("title")).append("\n");
                sb.append("Type: ").append(b.optString("type")).append("\n");
                sb.append("Severity: ").append(b.optString("severity")).append("\n");
                sb.append("Expected: ").append(b.optString("expected")).append("\n");
                sb.append("Actual: ").append(b.optString("actual")).append("\n");
                sb.append("Evidence: ").append(b.optString("evidence")).append("\n");
                JSONArray steps = b.optJSONArray("stepsToReproduce");
                if (steps != null) {
                    sb.append("StepsToReproduce:\n");
                    for (int i = 0; i < steps.length(); i++) {
                        sb.append("  - ").append(steps.getString(i)).append("\n");
                    }
                }
                sb.append("====================================\n\n");
            }

            utils.FilesManager.writeFile(bugsFile, sb.toString());

        } catch (Exception e) {
            utils.LogsManager.error("Failed writing bugs file: " + e.getMessage());
        }
    }
    private static String icon(boolean ok) { return ok ? "PASS " : "FAIL "; }

    private static String safe(String s) {
        return (s == null) ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String shortenError(String msg) {
        if (msg == null) return "";
        String m = msg.replaceAll("\\s+", " ").trim();

        String[] cutMarkers = {
                "(Session info:", "Build info:", "System info:", "Driver info:",
                "Capabilities", "Command:", "For documentation"
        };
        for (String marker : cutMarkers) {
            int idx = m.indexOf(marker);
            if (idx > 0) { m = m.substring(0, idx).trim(); }
        }

        int max = 220;
        if (m.length() > max) m = m.substring(0, max) + "...";
        return m;
    }
}
