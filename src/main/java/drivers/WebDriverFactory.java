package drivers;
import utils.LogsManager;
import handlingLayer.AlertsHandler;
import handlingLayer.BrowserHandler;
import handlingLayer.ElementsHandler;
import handlingLayer.FramesHandler;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ThreadGuard;
import utils.Config.AppConfigProvider;

public class WebDriverFactory {

    public final static String browser = AppConfigProvider.get().browserType();
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
        WebDriver d = driverThreadLocal.get();
        if (d != null) {
            d.quit();
        }
        driverThreadLocal.remove();
    }

    /** Tear down and create a fresh browser — clears in-memory app session state. */
    public void restart() {
        quit();
        Browser browserType = Browser.valueOf(browser.toUpperCase());
        AbstractDriver abstractDriver = browserType.getDriverFactory();
        LogsManager.info("Restarting Driver for Browser Type: " + browserType);
        WebDriver driver = ThreadGuard.protect(abstractDriver.createDriver());
        driverThreadLocal.set(driver);
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




}
