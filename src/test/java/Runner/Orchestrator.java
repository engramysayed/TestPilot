package Runner;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;
import utils.LogsManager;
import static buildersLayer.utilsBuilders.OutputBuilder.appendFinalInsights;
import static buildersLayer.utilsBuilders.OutputBuilder.recordBugs;

public class Orchestrator extends BaseOrchestrator {


    @Test
    public void runScenario() throws Exception {

        //prepare pre steps -> take initial screenshot -> open website -> login to prevent leaking data
        //then building the start prompt
            helper
                .beforeStart(runFolder)
                .buildStartState();

        while (!stopTesting) {

            //ask the llm for the next batch of actions to execute
            //parse them into readable data
            helper
                    .askLLM(planner, cycleCounter)
                    .parsingBatch();

            //execute the steps for current batch
            //build the update state prompt
            helper
                    .executeBatch( runFolder, cycleCounter, DEFAULT_WAIT, DEFAULT_SCREENSHOT_WAIT)
                    .buildUpdateState();

            //if stop testing =true means we finished the test
            stopTesting = helper.getStopTesting();
            if (stopTesting) break;


            cycleCounter++; //increment cycle counter
        }
        appendFinalInsights(helper);
        recordBugs(helper);
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
