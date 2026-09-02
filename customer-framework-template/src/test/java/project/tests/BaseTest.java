package project.tests;

import org.openqa.selenium.WebDriver;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;
import project.drivers.WebDriverFactory;
import project.drivers.WebDriverProvider;
import project.utils.dataReader.JsonReader;

@Listeners({project.listeners.TestNGListeners.class})
public class BaseTest implements WebDriverProvider {

    protected WebDriverFactory driver;
    protected JsonReader testData;


    @Override
    public WebDriver getWebDriver() {
        return driver.get();
    }

    @BeforeSuite
    public void initialize() {
        project.utils.dataReader.PropertyReader.loadProperties();
        testData = new JsonReader("login-data.json");
    }


}
