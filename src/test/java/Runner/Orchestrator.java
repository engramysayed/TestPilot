package Runner;
import helpers.utilsBuilders.OutputBuilder;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;
import utils.LogsManager;

import static helpers.utilsBuilders.OutputBuilder.recordBugs;

public class Orchestrator extends BaseOrchestrator {


    @Test
    public void runScenario() throws Exception {

        //prepare pre steps -> take initial screenshot -> open website -> login to prevent leaking data
        //then building the start prompt
            helper
                .beforeStart(runFolder)
                .buildStartState();

        while (!stopTesting) {

            helper
                    .askLLM(planner, cycleCounter)
                    .parsingBatch();

            helper
                    .executeBatch( runFolder, cycleCounter, DEFAULT_WAIT, DEFAULT_SCREENSHOT_WAIT)
                    .buildUpdateState();

            stopTesting = helper.getStopTesting();
            if (stopTesting) break;


            cycleCounter++; //increment cycle counter
        }
        recordBugs();
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
