package executionLayer;
import utils.LogsManager;
import utils.AppConfig;
import utils.AppConfigProvider;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static executionLayer.SelectorParser.toBy;
import static buildersLayer.mainBuilder.OrchestratorBuilder.storeVars;
import static buildersLayer.stateBuilders.StateVars.setLastScreenshotRef;

public class PreStartActions {
    private static final AppConfig CONFIG = AppConfigProvider.get();

    public static void preStepsActions(Path runFolder , actionExecute executor ){
        firstScreen(executor,runFolder);
        preStepsUrl(executor);
        if (CONFIG.isLogin()) {
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

            executor.elementAction("type",toBy(CONFIG.userNameLocator())
                    ,CONFIG.username());
            executor.elementAction("type",toBy(CONFIG.passwordLocator())
                    ,CONFIG.password());
            executor.elementAction("click",toBy(CONFIG.clickLocator()),"");

            TimeUnit.SECONDS.sleep(40);
        } catch (Exception e) {
            LogsManager.error("Error in preActions "+e);
        }

    }

    private static void preStepsUrl(actionExecute executor){
        executor.browserAction("navigate", CONFIG.baseWeb(),"");
    }



}
