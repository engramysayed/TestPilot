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

    private static ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    public WebDriverFactory() {
        startDriver("Starting");
    }

    private static String liveBrowserType() {
        String raw = AppConfigProvider.get().browserType();
        if (raw == null || raw.isBlank()) {
            return "EDGE";
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void startDriver(String verb) {
        Browser browserType = Browser.valueOf(liveBrowserType());
        AbstractDriver abstractDriver = browserType.getDriverFactory();
        LogsManager.info(verb + " Driver for Browser Type: " + browserType);
        WebDriver raw = abstractDriver.createDriver();
        delivery.job.ProveWiring.onDriverCreated(raw); // A2 closed-shadow hook when serializer=v2 (no-op by default)
        WebDriver driver = ThreadGuard.protect(raw);
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
        startDriver("Restarting");
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
