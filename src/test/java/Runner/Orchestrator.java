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
                    .askLLM(planner, cycleCounter) //stepCounter acts as batchId
                    .parsingBatch(DEFAULT_WAIT, DEFAULT_SCREENSHOT_WAIT);

            stopTesting = helper.isStopTesting();
            if (stopTesting) break;

            helper
                    .executeBatch(executor, runFolder, cycleCounter, DEFAULT_WAIT, DEFAULT_SCREENSHOT_WAIT)
                    .buildUpdateState();

            cycleCounter++; //increment cycle counter
        }
    }



    @AfterSuite
    public void closeDriver() {
        try {
            driver.quit();
        } catch (Exception e) {
            LogsManager.error("error closing driver "+e);
        }
    }


}
