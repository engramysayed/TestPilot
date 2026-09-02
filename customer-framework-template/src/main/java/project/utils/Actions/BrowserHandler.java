package project.utils.Actions;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.utils.Logs.LogsManager;

public class BrowserHandler {
    private static final Logger log = LoggerFactory.getLogger(BrowserHandler.class);
    private final WebDriver driver;

    public BrowserHandler(WebDriver driver) {
        this.driver = driver;
    }

    public void maximizeWindow() {
      try {
          driver.manage().window().maximize();
          LogsManager.info("Driver Maximized");
      } catch (Exception e) {
      LogsManager.error("Failed to maximize driver "+e.getMessage());
      }
    }

    public void navigateToUrl(String url) {
      try {
          driver.get(url);
          LogsManager.info("Navigated to URL " + url);
      } catch (Exception e) {
              LogsManager.error("Can't Get URL "+e.getMessage());
      }
    }

    public void refreshPage() {
      try {
          driver.navigate().refresh();
          LogsManager.info("Driver Refreshed");
      } catch (Exception e) {
         LogsManager.error("Driver Refresh Failed "+e.getMessage());
      }
    }

    public void navigateBack() {
       try {
           driver.navigate().back();
           LogsManager.info("Driver Navigated Back");
       } catch (Exception e) {
           LogsManager.error("Failed to navigate back "+e.getMessage());
       }

    }

    public String getCurrentUrl() {
      try{
          String text=driver.getCurrentUrl();
          LogsManager.info("Current URL "+text);
          return !(text.isEmpty())?text:null;
             } catch (Exception e) {
        LogsManager.error("Failed to get current url "+e.getMessage());
        return null;
             }
    }

    public void closeWindow() {
        try {
            driver.close();
            LogsManager.info("Driver Closed");
        } catch (Exception e) {
            LogsManager.error("Failed to close driver "+e.getMessage());
        }

    }

    public void newWindow(){
      try {
          driver.switchTo().newWindow(WindowType.TAB);
          LogsManager.info("Switched To New TAB");
      } catch (Exception e) {
          LogsManager.error("Failed Switch To New TAB "+e.getMessage());
      }
    }

    public void customTab(String tab) {
        try {
            driver.switchTo().window(tab);
           LogsManager.info("Switched To TAB "+tab);
         } catch (Exception e) {
        LogsManager.error("Failed Switch To New TAB "+tab+" "+e.getMessage());
         }
    }

}
