package handlingLayer;

import utils.LogsManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserHandler {
    private static final Logger log = LoggerFactory.getLogger(BrowserHandler.class);
    private final WebDriver driver;

    public BrowserHandler(WebDriver driver) {
        this.driver = driver;
    }

    public String maximizeWindow() {
      try {
          driver.manage().window().maximize();
          LogsManager.info("Driver Maximized");
          return "true";
      } catch (Exception e) {
      LogsManager.error("Failed to maximize driver "+e.getMessage());
          return "false";
      }
    }

    public String navigateToUrl(String url) {
      try {
          driver.get(url);
          LogsManager.info("Navigated to URL " + url);
          return "true";
      } catch (Exception e) {
              LogsManager.error("Can't Get URL "+e.getMessage());
              return "false";
      }
    }

    public String refreshPage() {
      try {
          driver.navigate().refresh();
          LogsManager.info("Driver Refreshed");
          return "true";
      } catch (Exception e) {
         LogsManager.error("Driver Refresh Failed "+e.getMessage());
          return "false";
      }
    }

    public String navigateBack() {
       try {
           driver.navigate().back();
           LogsManager.info("Driver Navigated Back");
           return "true";
       } catch (Exception e) {
           LogsManager.error("Failed to navigate back "+e.getMessage());
           return "false";
       }

    }

    public String getCurrentUrl() {
      try{
          String text=driver.getCurrentUrl();
          LogsManager.info("Current URL "+text);
          if (!text.isEmpty()) {
              return text;
          }
          return null;
             } catch (Exception e) {
        LogsManager.error("Failed to get current url "+e.getMessage());
        return "false";
             }
    }

    public String closeWindow() {
        try {
            driver.close();
            LogsManager.info("Driver Closed");
            return "true";
        } catch (Exception e) {
            LogsManager.error("Failed to close driver "+e.getMessage());
            return "false";
        }

    }

    public String newWindow(){
      try {
          driver.switchTo().newWindow(WindowType.TAB);
          LogsManager.info("Switched To New TAB");
          return "true";
      } catch (Exception e) {
          LogsManager.error("Failed Switch To New TAB "+e.getMessage());
          return "false";
      }
    }

    public String customTab(String tab) {
        try {
            driver.switchTo().window(tab);
           LogsManager.info("Switched To TAB "+tab);
            return "true";
         } catch (Exception e) {
        LogsManager.error("Failed Switch To New TAB "+tab+" "+e.getMessage());
            return "false";
         }
    }
    public String getPageSource() {
      try {
          return driver.getPageSource();
      } catch (Exception e) {
          LogsManager.error("Failed to get page source " + e.getMessage());
          return null;
      }
    }

}
