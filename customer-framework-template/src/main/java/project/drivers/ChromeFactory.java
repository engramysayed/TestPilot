package project.drivers;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import project.utils.dataReader.PropertyReader;

public class ChromeFactory extends AbstractDriver{

    private ChromeOptions getOptions(){
        ChromeOptions options=new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        options.addArguments("--start-maximized");
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        options.setCapability(CapabilityType.UNHANDLED_PROMPT_BEHAVIOUR, UnexpectedAlertBehaviour.IGNORE);
        options.setCapability(CapabilityType.ENABLE_DOWNLOADS, true);
        options.setAcceptInsecureCerts(true);
        String execution = executionType();
        if (isHeadless(execution)) {
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
        } else if ("Remote".equalsIgnoreCase(execution)) {
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-extensions");
            options.addArguments("--headless=new");
        }
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        return options;
     }

    @Override
    public WebDriver createDriver() {
        String execution = executionType();
        if (isLocal(execution)) {
            return new ChromeDriver(getOptions());
        }
        throw new IllegalStateException("Unsupported EXECUTION_TYPE: " + execution);
    }

    private static String executionType() {
        String type = PropertyReader.getProperty("EXECUTION_TYPE");
        return type == null || type.isBlank() ? "LOCAL" : type.trim();
    }

    private static boolean isLocal(String execution) {
        return "LOCAL".equalsIgnoreCase(execution)
                || "HEADLESS".equalsIgnoreCase(execution)
                || "LocalHeadless".equalsIgnoreCase(execution);
    }

    private static boolean isHeadless(String execution) {
        if ("HEADLESS".equalsIgnoreCase(execution) || "LocalHeadless".equalsIgnoreCase(execution)) {
            return true;
        }
        String flag = PropertyReader.getProperty("BROWSER_HEADLESS");
        return flag != null && ("true".equalsIgnoreCase(flag.trim()) || "1".equals(flag.trim()));
    }
}
