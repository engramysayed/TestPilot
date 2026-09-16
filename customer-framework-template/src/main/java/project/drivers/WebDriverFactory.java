package project.drivers;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ThreadGuard;
import project.utils.Actions.AlertsHandler;
import project.utils.Actions.BrowserHandler;
import project.utils.Actions.ElementsHandler;
import project.utils.Actions.FramesHandler;
import project.utils.Logs.LogsManager;
import project.utils.dataReader.PropertyReader;
import project.validations.Validation;
import project.validations.Verification;

import java.nio.file.Files;
import java.nio.file.Path;

public class WebDriverFactory {

    public final static String browser = PropertyReader.getProperty("BROWSER_TYPE");
    private static ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    public WebDriverFactory() {
        Browser browserType= Browser.valueOf(browser.toUpperCase());
        AbstractDriver abstractDriver = browserType.getDriverFactory();
        LogsManager.info("Starting Driver for Browser Type: " + browserType);
        WebDriver driver= ThreadGuard.protect(abstractDriver.createDriver());
        driverThreadLocal.set(driver);
    }

    public WebDriver get() {
        return driverThreadLocal.get();
    }

    public void quit(){
        WebDriver driver = driverThreadLocal.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
            LogsManager.info("Quitting Driver");
            writeQuitMarker();
        } catch (Exception e) {
            LogsManager.error("Error quitting driver: " + e.getMessage());
        } finally {
            driverThreadLocal.remove();
        }
    }

    private static void writeQuitMarker() {
        try {
            Path dir = Path.of("test-output");
            Files.createDirectories(dir);
            String name = "driver-quit-" + Thread.currentThread().threadId() + "-"
                    + System.nanoTime() + ".marker";
            Files.writeString(dir.resolve(name), "quit");
        } catch (Exception ignored) {
            // Marker is observational evidence for downloaded replay; quit must still complete.
        }
    }

    public ElementsHandler element(){
        return new ElementsHandler(get());
    }

    public BrowserHandler browser(){
        return new BrowserHandler(get());
    }

    public FramesHandler frames(){
        return new FramesHandler(get());
    }

    public AlertsHandler alerts(){
        return new AlertsHandler(get());
    }

    public Validation validation(){
        return new Validation(get());
    }

    public Verification verification(){
        return new Verification(get());
    }


}
