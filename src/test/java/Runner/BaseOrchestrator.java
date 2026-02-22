package Runner;

import llmLayer.LLMPlanner;
import drivers.WebDriverFactory;
import drivers.WebDriverProvider;
import executionLayer.actionExecute;
import helpers.OrchestratorHelper;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.BeforeSuite;
import utils.LogsManager;
import utils.PropertyReader;
import java.nio.file.Path;
public class BaseOrchestrator implements WebDriverProvider {

    protected WebDriverFactory driver;
    protected int HTML_MAX_CHARS , DEFAULT_WAIT , DEFAULT_SCREENSHOT_WAIT , cycleCounter = 1;
    protected Path runFolder;
    protected String scenario ;
    protected boolean stopTesting = false;
    protected LLMPlanner planner;
    protected actionExecute executor;
    protected OrchestratorHelper helper;

    @Override
    public WebDriver getWebDriver() {
        return driver.get();
    }

    @BeforeSuite
    public void initialize() {
        LogsManager.cleanRunLog();

        PropertyReader.loadProperties();

        HTML_MAX_CHARS = Integer.parseInt(PropertyReader.getProperty("HTML_MAX_CHARS"));
        DEFAULT_WAIT = Integer.parseInt(PropertyReader.getProperty("DEFAULT_WAIT"));
        DEFAULT_SCREENSHOT_WAIT = Integer.parseInt(PropertyReader.getProperty("DEFAULT_SCREENSHOT_WAIT"));
        scenario = PropertyReader.getProperty("SCENARIO");

        runFolder = OrchestratorHelper.createNewRunFolder();

        driver = new WebDriverFactory();
        planner = new LLMPlanner(runFolder);
        executor = new actionExecute(driver);
        helper = new OrchestratorHelper(driver, 0, scenario, HTML_MAX_CHARS);
    }




}
