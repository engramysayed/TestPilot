package buildersLayer.mainBuilder;
import llmLayer.LLMPlanner;
import drivers.WebDriverFactory;
import executionLayer.actionExecute;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import parsingLayer.JsonMapper;
import utils.LogsManager;
import utils.RuntimeSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static executionLayer.PreStartActions.preStepsActions;
import static executionLayer.SelectorParser.toBy;
import static buildersLayer.stateBuilders.Builder.*;
import static buildersLayer.stateBuilders.StateVars.*;
import static buildersLayer.utilsBuilders.OutputBuilder.*;

public class OrchestratorBuilder {
    private  WebDriverFactory driver;
    private String llmResponse,batchDetails = "",currentObservation = "",finalSummary = "";
    private int stepId;
    private boolean stopTesting, stopAfterBatch = false;
    private List<String[]> plannedSteps = new ArrayList<>() ;
    private final List<JSONObject> finalBugs = new ArrayList<>();
    private JSONArray executedStepsJson = new JSONArray();
    private final actionExecute executor;


    public OrchestratorBuilder(WebDriverFactory driver, int stepId){
        this.driver = driver;
        this.stepId=stepId;
        this.executor = new actionExecute(driver);
    }



    //open url + login (if required)
    public OrchestratorBuilder beforeStart(Path runFolder) {
        preStepsActions(runFolder,executor);
        return this;
    }

    //building the start state prompt
    public void buildStartState(){startState(executor);}

    //calling the llm
    public OrchestratorBuilder askLLM(LLMPlanner planner, int cycleId) {
        try {
            LogsManager.info("Requesting next batch from LLM. cycleId=" + cycleId);
            llmResponse = planner.getNextBatch(cycleId , getStateJson() , getLastScreenshotRef());
        } catch (Exception e) {
            LogsManager.error("Error in LLM request " + e);
            llmResponse = null;
        }
        return this;
    }

    //parse the LLM response into steps and other details
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

    //execute the steps for current batch
    public OrchestratorBuilder executeBatch(
                                           Path runFolder,
                                           int cycleId,
                                           int DEFAULT_WAIT,
                                           int DEFAULT_SCREENSHOT_WAIT) {

        executedStepsJson = new org.json.JSONArray();
        int i=0;
        for (String[] step : plannedSteps) {

            stepId = Integer.parseInt(step[0]);
            String stepDetails = step[1];
            String actionType = step[2];
            String action = step[3];
            String selector = step[4];
            String value = step[5];
            int generalWait = validateTime(step[6], DEFAULT_WAIT);
            int screenshotWait = validateTime(step[7], DEFAULT_SCREENSHOT_WAIT);
            boolean screenshot = Boolean.parseBoolean(step[8]);
            stopTesting = Boolean.parseBoolean(step[9]);


            boolean success = true;
            String message = "";
            storeVars(generalWait, screenshotWait);

            By locator = null;
            if (selector != null) {
                if (!selector.isBlank()) {
                    locator = toBy(selector);
                }
            }

            //execute
            try {
                String result = executor.checkAction(
                        actionType,
                        action,
                        value,
                        value,
                        locator,
                        value
                );

                if (result == null || result.contains("false")) {
                    success = false;
                    message = "Action returned failure: " + result;
                } else {
                    message = "Action executed: " + result;
                }
                String shotName = "cycle_" + cycleId + "_step_" + (i + 1);
                executor.takeScreenshot(screenshot,runFolder,shotName);


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
        recordSummary(cycleId,batchDetails,executedStepsJson,executor);
        saveSummary();

        return this;
    }

    //build the update state prompt
    public void buildUpdateState(){updateState(executor);}

    public boolean getStopTesting() {
        if (stopTesting) {
            LogsManager.info("Ending loop.");
        }else
        {
            LogsManager.info("Looping..");
        }
        return stopTesting;
    }







    //check if the time given from the step is null and store it if not
    public static int validateTime(String givenTime, int defaultTime) {
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
    public static void storeVars(int time, int screenWaitTime){
        RuntimeSettings.setGlobalWait(time);
        RuntimeSettings.setScreenshotWait(screenWaitTime);
    }


    public String getFinalSummary() {
        return finalSummary;
    }
    public String getCurrentObservation() {
        return currentObservation;
    }
    public List<JSONObject> getBugs(){return finalBugs;}






}
