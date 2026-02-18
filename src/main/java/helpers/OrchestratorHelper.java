package helpers;

import aiLayer.LLMPlanner;
import drivers.WebDriverFactory;
import executionLayer.SelectorParser;
import executionLayer.actionExecute;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import parsingLayer.JsonMapper;
import utils.HtmlSlimmer;
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
    private String message = "OK" , urlParam = "" , tabParam = "" , result;
    private int stepId , HTML_MAX_CHARS , generalWait , screenshotWait;
    private  boolean screenshot , stopTesting ,success = true , started = false;
    private String lastScreenshotRef = "step_0.png";
    private List<String[]> plannedSteps = new ArrayList<>();
    private JSONArray executedStepsJson = new org.json.JSONArray();
    private String batchDetails = "";

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

        executor.takeScreenshot(runFolder, 0);
        lastScreenshotRef = "step_0.png";

        stateJson = JsonMapper.buildPlannerStart(
                scenario,
                driver.browser().getCurrentUrl(),
                HtmlSlimmer.slim(driver.browser().getPageSource(), HTML_MAX_CHARS),
                lastScreenshotRef
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

    public void parsingBatch(int DEFAULT_WAIT, int DEFAULT_SCREENSHOT_WAIT) {

        String clean = JsonMapper.buildResult(llmResponse);

        plannedSteps = JsonMapper.parseBatchToArrays(clean);
        JSONObject root = new JSONObject(clean);

        boolean batchStop = root.optBoolean("stopTesting", false);
        if (batchStop || plannedSteps.isEmpty()) {
            stopTesting = true;
            return;
        }

        batchDetails = root.optString("batchDetails", "");
        LogsManager.info("LLM Batch details: " + batchDetails);
        LogsManager.info("LLM Batch size: " + plannedSteps.size());
    }

    public void buildUpdateState(){
        stateJson = JsonMapper.buildPlannerUpdate(
                scenario,
                executedStepsJson,
                driver.browser().getCurrentUrl(),
                HtmlSlimmer.slim(driver.browser().getPageSource(), HTML_MAX_CHARS),
                lastScreenshotRef
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

                if (result == null || "false".equalsIgnoreCase(result)) {
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
            org.json.JSONObject executed = new org.json.JSONObject();
            executed.put("stepId", stepId);
            executed.put("stepDetails", stepDetails);
            executed.put("actionType", actionType);
            executed.put("action", action);
            executed.put("selector", selector);
            executed.put("value", value);

            org.json.JSONObject res = new org.json.JSONObject();
            res.put("success", success);
            res.put("message", message);
            executed.put("result", res);

            executedStepsJson.put(executed);

            if (stopTesting) break;
            i++;
        }

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

        Path runPath = Path.of(System.getProperty("user.dir"), "test-output", "run_" + ts);

        createDirectory(runPath.toString());
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

        TimeUnit.SECONDS.sleep(5);
        } catch (Exception e) {
            LogsManager.error("Error in preActions "+e);
        }

    }

    public void preStepsUrl(actionExecute executor){
        executor.browserAction("navigate",PropertyReader.getProperty("BASE_WEB"),"");
    }



}
