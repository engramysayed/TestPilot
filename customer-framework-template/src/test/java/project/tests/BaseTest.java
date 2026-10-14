package project.tests;

import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;
import project.drivers.WebDriverFactory;
import project.drivers.WebDriverProvider;
import project.utils.dataReader.JsonReader;
import project.utils.dataReader.PropertyReader;
import project.validations.Validation;

import java.nio.file.Files;
import java.nio.file.Path;

@Listeners({project.listeners.TestNGListeners.class, project.utils.retry.KeelRetryListener.class})
public class BaseTest implements WebDriverProvider {

    protected WebDriverFactory driver;
    protected JsonReader testData;

    @Override
    public WebDriver getWebDriver() {
        return driver == null ? null : driver.get();
    }

    @BeforeSuite
    public void initialize() {
        PropertyReader.loadProperties();
        // Optional fixture: only load when present so customer ZIPs without login-data.json stay clean.
        Path loginData = Path.of("src/test/resources/test-data/login-data.json");
        if (Files.isRegularFile(loginData)) {
            testData = new JsonReader("login-data.json");
        }
    }

    @BeforeMethod
    public void setUp() {
        Validation.clearCapturedPhrases();
        PropertyReader.loadProperties();
        driver = new WebDriverFactory();
        String base = PropertyReader.getProperty("BASE_WEB");
        if (base != null && !base.isBlank()) {
            driver.browser().navigateToUrl(base);
        }
        loginIfConfigured();
    }

    /**
     * Optional login hook after BASE_WEB navigate. Default no-op.
     * Generated tests with login chron calls override {@code setUp} (calling {@code super.setUp()})
     * and use {@code LoginPage.login(user, pass)} when available; hand-written suites may override
     * this method to call the same page API using properties (e.g. {@code LOGIN_USER}/{@code LOGIN_PASS}).
     */
    protected void loginIfConfigured() {
        // no-op — customer or generated override
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            Validation.assertAll();
        } finally {
            Validation.clearCapturedPhrases();
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /** Resolve a required properties key; fails fast when missing (used by generated tests). */
    protected static String requiredProperty(String key) {
        String v = PropertyReader.getProperty(key);
        if (v == null) {
            throw new IllegalStateException("Missing required test data key: " + key);
        }
        return v;
    }
}
