package project.drivers;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;
import project.utils.Logs.LogsManager;
import project.utils.dataReader.PropertyReader;

import java.net.URI;
import java.util.Map;

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
        switch (PropertyReader.getProperty("EXECUTION_TYPE")) {
            case "LocalHeadless" -> options.addArguments("--headless=new");
            case "Remote" -> {
                options.addArguments("--disable-gpu");
                options.addArguments("--disable-extensions");
                options.addArguments("--headless=new");
            }
        }
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        return options;
     }

    @Override
    public WebDriver createDriver() {
        if(PropertyReader.getProperty("EXECUTION_TYPE").equalsIgnoreCase("HEADLESS")
                ||PropertyReader.getProperty("EXECUTION_TYPE").equalsIgnoreCase("LOCAL")) {
            return new ChromeDriver(getOptions());
        }else{
            try {
               // return new RemoteWebDriver(
                    //    new URI("http://" + remoteHost + ":" + remotePort + "/wd/hub").toURL(), getOptions()
               // );
            } catch (Exception e) {
                LogsManager.error("Error creating RemoteWebDriver: " + e.getMessage());
                throw new RuntimeException("Failed to create RemoteWebDriver", e);
            }
         }

        return null;
    }
}
