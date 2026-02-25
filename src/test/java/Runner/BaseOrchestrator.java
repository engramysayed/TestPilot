package Runner;
import llmLayer.LLMPlanner;
import drivers.WebDriverFactory;
import drivers.WebDriverProvider;
import helpers.mainHelper.OrchestratorHelper;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.BeforeSuite;
import utils.LogsManager;
import utils.PropertyReader;
import java.nio.file.Path;
import static helpers.utilsBuilders.OutputBuilder.createNewRunFolder;

public class BaseOrchestrator implements WebDriverProvider {

    protected WebDriverFactory driver;
    protected int   DEFAULT_WAIT , DEFAULT_SCREENSHOT_WAIT , cycleCounter = 1;
    protected Path runFolder;
    protected boolean stopTesting = false;
    protected LLMPlanner planner;
    protected OrchestratorHelper helper;

    @Override
    public WebDriver getWebDriver() {
        return driver.get();
    }

    @BeforeSuite
    public void initialize() {
        LogsManager.cleanRunLog();

        PropertyReader.loadProperties();

        DEFAULT_WAIT = Integer.parseInt(PropertyReader.getProperty("DEFAULT_WAIT"));
        DEFAULT_SCREENSHOT_WAIT = Integer.parseInt(PropertyReader.getProperty("DEFAULT_SCREENSHOT_WAIT"));

        runFolder = createNewRunFolder(runFolder);

        driver = new WebDriverFactory();
        planner = new LLMPlanner(runFolder);
        helper = new OrchestratorHelper(driver, 0);
    }




}
