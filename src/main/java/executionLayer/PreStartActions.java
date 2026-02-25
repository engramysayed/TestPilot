package executionLayer;
import utils.LogsManager;
import utils.PropertyReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static executionLayer.SelectorParser.toBy;
import static buildersLayer.mainHelper.OrchestratorBuilder.storeVars;
import static buildersLayer.stateBuilders.StateVars.setLastScreenshotRef;

public class PreStartActions {

    public static void preStepsActions(Path runFolder , actionExecute executor ){
        firstScreen(executor,runFolder);
        preStepsUrl(executor);
        if ((PropertyReader.getProperty("ISLOGIN").toLowerCase()).equalsIgnoreCase("true")) {
            preStepsLogin(executor);
        }
    }


    private static void firstScreen(actionExecute executor,Path runFolder){
        executor.takeScreenshot(runFolder, 0);
        setLastScreenshotRef("step_0.png");
    }

    private static void preStepsLogin(actionExecute executor){
        try {
            storeVars(5,1);

            executor.elementAction("type",toBy(PropertyReader.getProperty("userNameLocator"))
                    ,PropertyReader.getProperty("USERNAME"));
            executor.elementAction("type",toBy(PropertyReader.getProperty("passwordLocator"))
                    ,PropertyReader.getProperty("PASSWORD"));
            executor.elementAction("click",toBy(PropertyReader.getProperty("clickLocator")),"");

            TimeUnit.SECONDS.sleep(40);
        } catch (Exception e) {
            LogsManager.error("Error in preActions "+e);
        }

    }

    private static void preStepsUrl(actionExecute executor){
        executor.browserAction("navigate",PropertyReader.getProperty("BASE_WEB"),"");
    }



}
