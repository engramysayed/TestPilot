package Runner;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;
import utils.LogsManager;
import utils.PropertyReader;

public class Orchestrator extends BaseOrchestrator {


    @Test
    public void runScenario() throws Exception {

        //prepare pre steps -> open website -> login to prevent leaking data
        helper.preStepsUrl(executor);
        if ((PropertyReader.getProperty("ISLOGIN").toLowerCase()).equalsIgnoreCase("true")) {
            helper.preStepsActions(executor);
        }


        //build starting prompt
        helper.buildStartState(executor, runFolder);

        while (!stopTesting) {

            helper
                    .askLLM(planner, cycleCounter)
                    .parsingBatch();

            helper
                    .executeBatch(executor, runFolder, cycleCounter, DEFAULT_WAIT, DEFAULT_SCREENSHOT_WAIT)
                    .buildUpdateState();

            stopTesting = helper.isStopTesting();
            if (stopTesting) break;


            cycleCounter++; //increment cycle counter
        }
        helper.recordBugs();
        LogsManager.info("FINAL SUMMARY: " + helper.getFinalSummary());
        LogsManager.info("CURRENT OBSERVATION: " + helper.getCurrentObservation());
    }



    @AfterSuite
    public void closeDriver() {
        try {
          //  driver.quit();
        } catch (Exception e) {
            LogsManager.error("error closing driver "+e);
        }
    }


}
