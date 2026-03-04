package Runner;
import llmLayer.LLMPlanner;
import drivers.WebDriverFactory;
import buildersLayer.mainBuilder.OrchestratorBuilder;
import org.testng.annotations.BeforeSuite;
import utils.Config.AppConfig;
import utils.Config.AppConfigProvider;
import utils.LogsManager;
import java.nio.file.Path;
import static buildersLayer.utilsBuilders.OutputBuilder.createNewRunFolder;

public class BaseOrchestrator {

    protected WebDriverFactory driver;
    protected int   DEFAULT_WAIT , DEFAULT_SCREENSHOT_WAIT , cycleCounter = 1;
    protected Path runFolder;
    protected boolean stopTesting = false;
    protected LLMPlanner planner;
    protected OrchestratorBuilder helper;

    @BeforeSuite
    public void initialize() {
        LogsManager.cleanRunLog();
        AppConfig config = AppConfigProvider.get();
        DEFAULT_WAIT = config.defaultWait();
        DEFAULT_SCREENSHOT_WAIT = config.defaultScreenshotWait();

        runFolder = createNewRunFolder(runFolder);

        driver = new WebDriverFactory();
        planner = new LLMPlanner(runFolder);
        helper = new OrchestratorBuilder(driver, 0);
    }




}
